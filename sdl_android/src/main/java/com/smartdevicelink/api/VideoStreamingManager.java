package com.smartdevicelink.api;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.Surface;

import com.smartdevicelink.SdlConnection.SdlSession;
import com.smartdevicelink.encoder.SdlEncoder;
import com.smartdevicelink.encoder.VirtualDisplayEncoder;
import com.smartdevicelink.haptic.HapticInterfaceManager;
import com.smartdevicelink.protocol.enums.FunctionID;
import com.smartdevicelink.protocol.enums.SessionType;
import com.smartdevicelink.proxy.RPCNotification;
import com.smartdevicelink.proxy.interfaces.ISdl;
import com.smartdevicelink.proxy.interfaces.ISdlServiceListener;
import com.smartdevicelink.proxy.interfaces.IVideoStreamListener;
import com.smartdevicelink.proxy.interfaces.OnSystemCapabilityListener;
import com.smartdevicelink.proxy.rpc.DisplayCapabilities;
import com.smartdevicelink.proxy.rpc.ImageResolution;
import com.smartdevicelink.proxy.rpc.OnHMIStatus;
import com.smartdevicelink.proxy.rpc.OnTouchEvent;
import com.smartdevicelink.proxy.rpc.TouchCoord;
import com.smartdevicelink.proxy.rpc.TouchEvent;
import com.smartdevicelink.proxy.rpc.VideoStreamingCapability;
import com.smartdevicelink.proxy.rpc.enums.HMILevel;
import com.smartdevicelink.proxy.rpc.enums.SystemCapabilityType;
import com.smartdevicelink.proxy.rpc.enums.TouchType;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCNotificationListener;
import com.smartdevicelink.streaming.video.SdlRemoteDisplay;
import com.smartdevicelink.streaming.video.VideoStreamingParameters;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.FutureTask;

@TargetApi(19)
public class VideoStreamingManager extends BaseSubManager{
	private static String TAG = "VideoStreamingManager";

	private WeakReference<Context> context;
	private volatile VirtualDisplayEncoder encoder;
	private Class<? extends SdlRemoteDisplay> remoteDisplayClass = null;
	private SdlRemoteDisplay remoteDisplay;
	private float[] touchScalar = {1.0f,1.0f}; //x, y
	private HapticInterfaceManager hapticManager;
	private SdlMotionEvent sdlMotionEvent = null;
	private HMILevel hmiLevel;
	private StreamingStateMachine stateMachine;
	private SdlEncoder mSdlEncoder;

	// INTERNAL INTERFACES

	private final ISdlServiceListener serviceListener = new ISdlServiceListener() {
		@Override
		public void onServiceStarted(SdlSession session, SessionType type, boolean isEncrypted) {
			if(SessionType.NAV.equals(type)){
				stateMachine.transitionToState(StreamingStateMachine.READY);
			}
		}

		@Override
		public void onServiceEnded(SdlSession session, SessionType type) {
			if(SessionType.NAV.equals(type)){
				stateMachine.transitionToState(StreamingStateMachine.NONE);
				if(remoteDisplay!=null){
					stopStreaming();
				}
			}
		}

		@Override
		public void onServiceError(SdlSession session, SessionType type, String reason) {}
	};

	private final OnRPCNotificationListener hmiListener = new OnRPCNotificationListener() {
		@Override
		public void onNotified(RPCNotification notification) {
			hmiLevel = ((OnHMIStatus)notification).getHmiLevel();
		}
	};

	private final OnRPCNotificationListener touchListener = new OnRPCNotificationListener() {
		@Override
		public void onNotified(RPCNotification notification) {
			if(notification !=null && remoteDisplay != null){
				MotionEvent event = convertTouchEvent((OnTouchEvent)notification);
				if(event!=null){
					remoteDisplay.handleMotionEvent(event);
				}
			}
		}
	};

	// MANAGER APIs

	public VideoStreamingManager(ISdl internalInterface){
		super(internalInterface);

		encoder = new VirtualDisplayEncoder();
		hmiLevel = HMILevel.HMI_NONE;

		// Listen for video service events
		internalInterface.addServiceListener(SessionType.NAV, serviceListener);
		// Take care of the touch events
		internalInterface.addOnRPCNotificationListener(FunctionID.ON_TOUCH_EVENT, touchListener);
		// Listen for HMILevel changes
		internalInterface.addOnRPCNotificationListener(FunctionID.ON_HMI_STATUS, hmiListener);

		stateMachine = new StreamingStateMachine();
		transitionToState(BaseSubManager.READY);
	}

	/**
	 * Starts streaming a remote display to the module if there is a connected session. This method of streaming requires the device to be on API level 19 or higher
	 * @param context a context that can be used to create the remote display
	 * @param remoteDisplayClass class object of the remote display. This class will be used to create an instance of the remote display and will be projected to the module
	 * @param parameters streaming parameters to be used when streaming. If null is sent in, the default/optimized options will be used.
	 *                   If you are unsure about what parameters to be used it is best to just send null and let the system determine what
	 *                   works best for the currently connected module.
	 *
	 * @param encrypted a flag of if the stream should be encrypted. Only set if you have a supplied encryption library that the module can understand.
	 */
	public void startRemoteDisplayStream(Context context, Class<? extends SdlRemoteDisplay> remoteDisplayClass, VideoStreamingParameters parameters, final boolean encrypted){
		this.context = new WeakReference<>(context);
		this.remoteDisplayClass = remoteDisplayClass;
		if(internalInterface.getWiProVersion() >= 5 && !internalInterface.isCapabilitySupported(SystemCapabilityType.VIDEO_STREAMING)){
			Log.e(TAG, "Video streaming not supported on this module");
			stateMachine.transitionToState(StreamingStateMachine.ERROR);
			return;
		}
		if(parameters == null){
			if(internalInterface.getWiProVersion() >= 5) {
				internalInterface.getCapability(SystemCapabilityType.VIDEO_STREAMING, new OnSystemCapabilityListener() {
					@Override
					public void onCapabilityRetrieved(Object capability) {
						VideoStreamingParameters params = new VideoStreamingParameters();
						params.update((VideoStreamingCapability)capability);	//Streaming parameters are ready time to stream
						startVideoStreaming(params, encrypted);
					}

					@Override
					public void onError(String info) {
						stateMachine.transitionToState(StreamingStateMachine.ERROR);
						Log.e(TAG, "Error retrieving video streaming capability: " + info);
					}
				});
			}else{
				//We just use default video streaming params
				VideoStreamingParameters params = new VideoStreamingParameters();
				DisplayCapabilities dispCap = (DisplayCapabilities)internalInterface.getCapability(SystemCapabilityType.DISPLAY);
				if(dispCap !=null){
					params.setResolution(dispCap.getScreenParams().getImageResolution());
				}
				startVideoStreaming(params, encrypted);
			}
		}else{
			startVideoStreaming(parameters, encrypted);
		}
	}

	/**
	 * Opens the video service (serviceType 11) and creates a Surface (used for streaming video) with input parameters provided by the app
	 * @param frameRate - specified rate of frames to utilize for creation of Surface
	 * @param iFrameInterval - specified interval to utilize for creation of Surface
	 * @param width - specified width to utilize for creation of Surface
	 * @param height - specified height to utilize for creation of Surface
	 * @param bitrate - specified bitrate to utilize for creation of Surface
	 *@return Surface if service is opened successfully and stream is started, return null otherwise
	 */
	@SuppressWarnings("unused")
	public Surface createOpenGLInputSurface(int frameRate, int iFrameInterval, int width,
	                                        int height, int bitrate, boolean isEncrypted) {
		if(hmiLevel != HMILevel.HMI_FULL){
			Log.e(TAG, "Cannot start video service if HMILevel is not FULL.");
			return null;
		}

		IVideoStreamListener encoderListener = startVideoStream(new VideoStreamingParameters(), isEncrypted);
		if (encoderListener == null) {
			stateMachine.transitionToState(StreamingStateMachine.ERROR);
			Log.e(TAG, "Cannot create encoderListener");
			return null;
		}

		mSdlEncoder = new SdlEncoder();
		mSdlEncoder.setFrameRate(frameRate);
		mSdlEncoder.setFrameInterval(iFrameInterval);
		mSdlEncoder.setFrameWidth(width);
		mSdlEncoder.setFrameHeight(height);
		mSdlEncoder.setBitrate(bitrate);
		mSdlEncoder.setOutputListener(encoderListener);
		Surface surface = mSdlEncoder.prepareEncoder();

		if(surface != null){
			stateMachine.transitionToState(StreamingStateMachine.READY);
		}else{
			Log.e(TAG, "Cannot create surface.");
			stateMachine.transitionToState(StreamingStateMachine.ERROR);
		}
		return surface;
	}

	/**
	 * Starts the MediaCodec encoder utilized in conjunction with the Surface returned via the createOpenGLInputSurface method
	 * @see #createOpenGLInputSurface(int, int, int, int, int, boolean)
	 */
	public void startEncoder(){
		if(mSdlEncoder != null){
			mSdlEncoder.startEncoder();
			stateMachine.transitionToState(StreamingStateMachine.STARTED);
		}
	}

	/**
	 * Releases the MediaCodec encoder utilized in conjunction with the Surface returned via the createOpenGLInputSurface method
	 * @see #createOpenGLInputSurface(int, int, int, int, int, boolean)
	 */
	public void releaseEncoder(){
		if(mSdlEncoder != null){
			mSdlEncoder.releaseEncoder();
			stateMachine.transitionToState(StreamingStateMachine.STOPPED);
		}
	}

	/**
	 * Drains the MediaCodec encoder utilized in conjunction with the Surface returned via the createOpenGLInputSurface method
	 * @param endOfStream indicates if this is the end of stream
	 * @see #createOpenGLInputSurface(int, int, int, int, int, boolean)
	 */
	public void drainEncoder(boolean endOfStream){
		if(mSdlEncoder != null) {
			mSdlEncoder.drainEncoder(endOfStream);
		}
	}

	/**
	 * Opens a video service (service type 11) and subsequently provides an IVideoStreamListener
	 * to the app to send video data. The supplied VideoStreamingParameters will be set as desired paramaters
	 * that will be used to negotiate
	 *
	 * @param parameters  Video streaming parameters including: codec which will be used for streaming (currently, only
	 *                    VideoStreamingCodec.H264 is accepted), height and width of the video in pixels.
	 * @param encrypted Specify true if packets on this service have to be encrypted
	 *
	 * @return IVideoStreamListener interface if service is opened successfully and streaming is
	 *         started, null otherwise
	 */
	public IVideoStreamListener startVideoStream(VideoStreamingParameters parameters, boolean encrypted){
		if(hmiLevel != HMILevel.HMI_FULL){
			Log.e(TAG, "Cannot start video service if HMILevel is not FULL.");
			return null;
		}
		IVideoStreamListener listener = internalInterface.startVideoStream(encrypted, parameters);
		if(listener != null){
			stateMachine.transitionToState(StreamingStateMachine.STARTED);
		}else{
			stateMachine.transitionToState(StreamingStateMachine.ERROR);
		}
		return listener;
	}

	/**
	 * Starts video service, sets up encoder, haptic manager, and remote display. Begins streaming the remote display.
	 * @param parameters Video streaming parameters including: codec which will be used for streaming (currently, only
	 *                    VideoStreamingCodec.H264 is accepted), height and width of the video in pixels.
	 * @param encrypted Specify true if packets on this service have to be encrypted
	 */
	private void startVideoStreaming(VideoStreamingParameters parameters, boolean encrypted){
		IVideoStreamListener streamListener = startVideoStream(parameters, encrypted);
		if(streamListener == null){
			Log.e(TAG, "Error starting video service");
			stateMachine.transitionToState(StreamingStateMachine.ERROR);
			return;
		}
		VideoStreamingCapability capability = (VideoStreamingCapability) internalInterface.getCapability(SystemCapabilityType.VIDEO_STREAMING);
		if(capability != null && capability.getIsHapticSpatialDataSupported()){
			hapticManager = new HapticInterfaceManager(internalInterface);
		}
		try {
			encoder.init(this.context.get(),streamListener,parameters);
			//We are all set so we can start streaming at at this point
			encoder.start();
			//Encoder should be up and running
			createRemoteDisplay(encoder.getVirtualDisplay());
			stateMachine.transitionToState(StreamingStateMachine.STARTED);
		} catch (Exception e) {
			stateMachine.transitionToState(StreamingStateMachine.ERROR);
			e.printStackTrace();
		}
		Log.d(TAG, parameters.toString());
	}

	/**
	 * Stops streaming service and remote display encoder if applicable.
	 */
	public void stopStreaming(){
		if(remoteDisplay!=null){
			remoteDisplay.stop();
			remoteDisplay = null;
		}
		if(encoder!=null){
			encoder.shutDown();
		}
		if(internalInterface!=null){
			internalInterface.stopVideoService();
		}
		stateMachine.transitionToState(StreamingStateMachine.STOPPED);
	}

	/**
	 * Stops video streaming service and removes service listener.
	 */
	public void dispose(){
		stopStreaming();

		// Remove listeners
		internalInterface.removeServiceListener(SessionType.NAV, serviceListener);
		internalInterface.removeOnRPCNotificationListener(FunctionID.ON_TOUCH_EVENT, touchListener);
		internalInterface.removeOnRPCNotificationListener(FunctionID.ON_HMI_STATUS, hmiListener);

		stateMachine.transitionToState(StreamingStateMachine.NONE);
	}

	// PUBLIC METHODS FOR CHECKING STATE

	/**
	 * Check if a video service is currently active
	 * @return boolean (true = active, false = inactive)
	 */
	public boolean isVideoConnected(){
		return (stateMachine.getState() == StreamingStateMachine.READY) ||
				(stateMachine.getState() == StreamingStateMachine.STARTED) ||
				(stateMachine.getState() == StreamingStateMachine.STOPPED);
	}

	/**
	 * Check if video streaming has been paused due to app moving to background
	 * @return boolean (true = not paused, false = paused)
	 */
	public boolean isVideoStreamingPaused(){
		return (stateMachine.getState() == StreamingStateMachine.STARTED) &&
				(hmiLevel != HMILevel.HMI_FULL);
	}

	/**
	 * Gets the current video streaming state as defined in @StreamingStateMachine
	 * @return int representing StreamingStateMachine.StreamingState
	 */
	public @StreamingStateMachine.StreamingState int currentVideoStreamState(){
		return stateMachine.getState();
	}

	// HELPER METHODS

	private void createRemoteDisplay(final Display disp){
		try{
			if (disp == null){
				return;
			}

			// Dismiss the current presentation if the display has changed.
			if (remoteDisplay != null && remoteDisplay.getDisplay() != disp) {
				remoteDisplay.dismissPresentation();
			}

			FutureTask<Boolean> fTask =  new FutureTask<Boolean>( new SdlRemoteDisplay.Creator(context.get(), disp, remoteDisplay, remoteDisplayClass, new SdlRemoteDisplay.Callback(){
				@Override
				public void onCreated(final SdlRemoteDisplay remoteDisplay) {
					//Remote display has been created.
					//Now is a good time to do parsing for spatial data
					VideoStreamingManager.this.remoteDisplay = remoteDisplay;
					if(hapticManager != null) {
						remoteDisplay.getMainView().post(new Runnable() {
							@Override
							public void run() {
								hapticManager.refreshHapticData(remoteDisplay.getMainView());
							}
						});
					}
					//Get touch scalars
					ImageResolution resolution = null;
					if(internalInterface.getWiProVersion()>=5){ //At this point we should already have the capability
						VideoStreamingCapability capability = (VideoStreamingCapability) internalInterface.getCapability(SystemCapabilityType.VIDEO_STREAMING);
						resolution = capability.getPreferredResolution();
					}else {
						DisplayCapabilities dispCap = (DisplayCapabilities) internalInterface.getCapability(SystemCapabilityType.DISPLAY);
						if (dispCap != null) {
							resolution = (dispCap.getScreenParams().getImageResolution());
						}
					}
					if(resolution != null){
						DisplayMetrics displayMetrics = new DisplayMetrics();
						disp.getMetrics(displayMetrics);
						touchScalar[0] = ((float)displayMetrics.widthPixels) / resolution.getResolutionWidth();
						touchScalar[1] = ((float)displayMetrics.heightPixels) / resolution.getResolutionHeight();
					}

				}

				@Override
				public void onInvalidated(final SdlRemoteDisplay remoteDisplay) {
					//Our view has been invalidated
					//A good time to refresh spatial data
					if(hapticManager != null) {
						remoteDisplay.getMainView().post(new Runnable() {
							@Override
							public void run() {
								hapticManager.refreshHapticData(remoteDisplay.getMainView());
							}
						});
					}
				}
			} ));
			Thread showPresentation = new Thread(fTask);

			showPresentation.start();
		} catch (Exception ex) {
			Log.e(TAG, "Unable to create Virtual Display.");
		}
	}

	protected MotionEvent convertTouchEvent(OnTouchEvent touchEvent){
		List<TouchEvent> eventList = touchEvent.getEvent();
		if (eventList == null || eventList.size() == 0) return null;

		TouchType touchType = touchEvent.getType();
		if (touchType == null){ return null;}

		int eventListSize = eventList.size();

		MotionEvent.PointerProperties[] pointerProperties = new MotionEvent.PointerProperties[eventListSize];
		MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[eventListSize];

		TouchEvent event;
		MotionEvent.PointerProperties properties;
		MotionEvent.PointerCoords coords;
		TouchCoord touchCoord;

		for(int i = 0; i < eventListSize; i++){
			event = eventList.get(i);
			if(event == null || event.getId() == null || event.getTouchCoordinates() == null){
				continue;
			}

			properties = new MotionEvent.PointerProperties();
			properties.id = event.getId();
			properties.toolType = MotionEvent.TOOL_TYPE_FINGER;


			List<TouchCoord> coordList = event.getTouchCoordinates();
			if (coordList == null || coordList.size() == 0){ continue; }

			touchCoord = coordList.get(coordList.size() -1);
			if(touchCoord == null){ continue; }

			coords = new MotionEvent.PointerCoords();
			coords.x = touchCoord.getX() * touchScalar[0];
			coords.y = touchCoord.getY() * touchScalar[1];
			coords.orientation = 0;
			coords.pressure = 1.0f;
			coords.size = 1;

			//Add the info to lists only after we are sure we have all available info
			pointerProperties[i] = properties;
			pointerCoords[i] = coords;

		}


		if(sdlMotionEvent == null) {
			if (touchType == TouchType.BEGIN) {
				sdlMotionEvent = new SdlMotionEvent();
			}else{
				return  null;
			}
		}

		int eventAction = sdlMotionEvent.getMotionEvent(touchType, pointerProperties);
		long startTime = sdlMotionEvent.startOfEvent;

		//If the motion event should be finished we should clear our reference
		if(eventAction == MotionEvent.ACTION_UP || eventAction == MotionEvent.ACTION_CANCEL){
			sdlMotionEvent = null;
		}

		return MotionEvent.obtain(startTime, SystemClock.uptimeMillis(), eventAction, eventListSize, pointerProperties, pointerCoords, 0, 0,1,1,0,0, InputDevice.SOURCE_TOUCHSCREEN,0);
	}

	/**
	 * Keeps track of the current motion event for VPM
	 */
	private static class SdlMotionEvent{
		long startOfEvent;
		SparseIntArray pointerStatuses = new SparseIntArray();

		SdlMotionEvent(){
			startOfEvent = SystemClock.uptimeMillis();
		}

		/**
		 * Handles the SDL Touch Event to keep track of pointer status and returns the appropirate
		 * Android MotionEvent according to this events status
		 * @param touchType The SDL TouchType that was received from the module
		 * @param pointerProperties the parsed pointer properties built from the OnTouchEvent RPC
		 * @return the correct native Andorid MotionEvent action to dispatch
		 */
		synchronized int  getMotionEvent(TouchType touchType, MotionEvent.PointerProperties[] pointerProperties){
			int motionEvent = MotionEvent.ACTION_DOWN;
			switch (touchType){
				case BEGIN:
					if(pointerStatuses.size() == 0){
						//The motion event has just begun
						motionEvent = MotionEvent.ACTION_DOWN;
					}else{
						motionEvent = MotionEvent.ACTION_POINTER_DOWN;
					}
					setPointerStatuses(motionEvent, pointerProperties);
					break;
				case MOVE:
					motionEvent = MotionEvent.ACTION_MOVE;
					setPointerStatuses(motionEvent, pointerProperties);

					break;
				case END:
					//Clears out pointers that have ended
					setPointerStatuses(MotionEvent.ACTION_UP, pointerProperties);

					if(pointerStatuses.size() == 0){
						//The motion event has just ended
						motionEvent = MotionEvent.ACTION_UP;
					}else{
						motionEvent = MotionEvent.ACTION_POINTER_UP;
					}
					break;
				case CANCEL:
					//Assuming this cancels the entire event
					motionEvent = MotionEvent.ACTION_CANCEL;
					pointerStatuses.clear();
					break;
				default:
					break;
			}
			return motionEvent;
		}

		private void setPointerStatuses(int motionEvent, MotionEvent.PointerProperties[] pointerProperties){

			for(int i = 0; i < pointerProperties.length; i ++){
				MotionEvent.PointerProperties properties = pointerProperties[i];
				if(properties != null){
					if(motionEvent == MotionEvent.ACTION_UP || motionEvent == MotionEvent.ACTION_POINTER_UP){
						pointerStatuses.delete(properties.id);
					}else if(motionEvent == MotionEvent.ACTION_DOWN && properties.id == 0){
						pointerStatuses.put(properties.id, MotionEvent.ACTION_DOWN);
					}else{
						pointerStatuses.put(properties.id, motionEvent);
					}

				}
			}
		}
	}

}