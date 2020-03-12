package com.sdl.hellosdlandroid;

import android.text.TextUtils;
import android.util.Log;

import com.smartdevicelink.components.BaseSdlService;
import com.smartdevicelink.managers.CompletionListener;
import com.smartdevicelink.managers.SdlManager;
import com.smartdevicelink.managers.SdlManagerListener;
import com.smartdevicelink.managers.file.filetypes.SdlArtwork;
import com.smartdevicelink.managers.lifecycle.LifecycleConfigurationUpdate;
import com.smartdevicelink.managers.screen.choiceset.ChoiceCell;
import com.smartdevicelink.managers.screen.choiceset.ChoiceSet;
import com.smartdevicelink.managers.screen.choiceset.ChoiceSetSelectionListener;
import com.smartdevicelink.managers.screen.menu.MenuCell;
import com.smartdevicelink.managers.screen.menu.MenuSelectionListener;
import com.smartdevicelink.managers.screen.menu.VoiceCommand;
import com.smartdevicelink.managers.screen.menu.VoiceCommandSelectionListener;
import com.smartdevicelink.protocol.enums.FunctionID;
import com.smartdevicelink.proxy.RPCNotification;
import com.smartdevicelink.proxy.TTSChunkFactory;
import com.smartdevicelink.proxy.rpc.Alert;
import com.smartdevicelink.proxy.rpc.OnHMIStatus;
import com.smartdevicelink.proxy.rpc.Speak;
import com.smartdevicelink.proxy.rpc.enums.AppHMIType;
import com.smartdevicelink.proxy.rpc.enums.FileType;
import com.smartdevicelink.proxy.rpc.enums.HMILevel;
import com.smartdevicelink.proxy.rpc.enums.InteractionMode;
import com.smartdevicelink.proxy.rpc.enums.Language;
import com.smartdevicelink.proxy.rpc.enums.MenuLayout;
import com.smartdevicelink.proxy.rpc.enums.PredefinedWindows;
import com.smartdevicelink.proxy.rpc.enums.TriggerSource;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCNotificationListener;
import com.smartdevicelink.transport.TransportConfigHolder;
import com.smartdevicelink.util.DebugTool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

public class SdlService extends BaseSdlService {

	private static final String TAG 					= "SDL Service";

	private static final String APP_NAME 				= "Hello Sdl";
	private static final String APP_NAME_ES 			= "Hola Sdl";
	private static final String APP_NAME_FR 			= "Bonjour Sdl";
	private static final Integer FIRST_APP_ID 			= 8678311;
	private static final Integer SECOND_APP_ID 			= 8678310;
	private static final String SERVICE_NOTIFICATION_TITLE = "Connected through SDL";

	private static final String ICON_FILENAME 			= "hello_sdl_icon.png";
	private static final String SDL_IMAGE_FILENAME  	= "sdl_full_image.png";

	private static final String WELCOME_SHOW 			= "Welcome to HelloSDL";
	private static final String WELCOME_SPEAK 			= "Welcome to Hello S D L";

	private static final String TEST_COMMAND_NAME 		= "Test Command";

	private static final int FOREGROUND_SERVICE_ID = 111;

	// TCP/IP transport config
	// The default port is 12345
	// The IP is of the machine that is running SDL Core
	private static final int TCP_PORT = 12481;
	private static final String DEV_MACHINE_IP_ADDRESS = "m.sdl.tools";

	@Override
	public Integer provideServiceForegroundId() {
		return FOREGROUND_SERVICE_ID;
	}

	@Override
	public String provideServiceName() {
		return APP_NAME;
	}

	@Override
	public Integer provideServiceIcon() {
		return R.drawable.ic_sdl;
	}

	@Override
	public String provideServiceNotificationTitle() {
		return SERVICE_NOTIFICATION_TITLE;
	}

	@Override
	public void configure() {
        // This logic is to select the correct transport and security levels defined in the selected build flavor
        // Build flavors are selected by the "build variants" tab typically located in the bottom left of Android Studio
        // Typically in your app, you will only set one of these.
        if (sdlManagerMap.size() == 0) {
            Log.i(TAG, "Starting SDL Proxy");
            // Enable DebugTool for debug build type
            if (BuildConfig.DEBUG){
                DebugTool.enableDebugTool();
            }

            // Create App Icon, this is set in the SdlManager builder
            SdlArtwork appIcon = new SdlArtwork(ICON_FILENAME, FileType.GRAPHIC_PNG, R.mipmap.ic_launcher, true);

            SdlManager firstApplicationManager = createManagerWithBaseConfiguration(
			appIcon,
					FIRST_APP_ID.toString()
			);
            SdlManager secondApplicationManager = createManagerWithBaseConfiguration(
			appIcon,
					SECOND_APP_ID.toString()
			);

			firstApplicationManager.start();
			firstApplicationManager.addOnRPCNotificationListener(FunctionID.ON_HMI_STATUS, new OnRPCNotificationListener() {
				@Override
				public void onNotified(RPCNotification notification, String applicationId) {
					OnHMIStatus onHMIStatus = (OnHMIStatus) notification;
					if (onHMIStatus.getWindowID() != null && onHMIStatus.getWindowID() != PredefinedWindows.DEFAULT_WINDOW.getValue()) {
						return;
					}
					if (onHMIStatus.getHmiLevel() == HMILevel.HMI_FULL && onHMIStatus.getFirstRun()) {
						if (applicationId == null || TextUtils.isEmpty(applicationId)) {
							throw new AssertionError("ApplicationId can't be null or empty");
						}
						SdlManager manager = sdlManagerMap.get(Integer.parseInt(applicationId));
						setVoiceCommands(manager);
						sendMenus(manager);
					}
				}
			});

			secondApplicationManager.start();
			secondApplicationManager.addOnRPCNotificationListener(FunctionID.ON_HMI_STATUS, new OnRPCNotificationListener() {
				@Override
				public void onNotified(RPCNotification notification, String applicationId) {
					OnHMIStatus onHMIStatus = (OnHMIStatus) notification;
					if (onHMIStatus.getWindowID() != null && onHMIStatus.getWindowID() != PredefinedWindows.DEFAULT_WINDOW.getValue()) {
						return;
					}
					if (onHMIStatus.getHmiLevel() == HMILevel.HMI_FULL && onHMIStatus.getFirstRun()) {
						SdlManager manager = sdlManagerMap.get(Integer.parseInt(applicationId));
						performWelcomeSpeak(manager);
						performWelcomeShow(manager);
						preloadChoices(manager);
					}
				}
			});


			// IMPORTANT
			// ADD EVERY SDLMANAGER TO CONTAINER
			sdlManagerMap.put(FIRST_APP_ID, firstApplicationManager);
			sdlManagerMap.put(SECOND_APP_ID, secondApplicationManager);
		}
	}

	private SdlManager createManagerWithBaseConfiguration(SdlArtwork appIcon, String appId) {
		SdlManager localSdlManager;
		// The app type to be used
		Vector<AppHMIType> appType = new Vector<>();
		appType.add(AppHMIType.DEFAULT);

		// The manager listener helps you know when certain events that pertain to the SDL Manager happen
		// Here we will listen for ON_HMI_STATUS and ON_COMMAND notifications
		SdlManagerListener listener = new SdlManagerListener() {
			@Override
			public void onStart() { }

			@Override
			public void onDestroy() { stopSelf(); }

			@Override
			public void onError(String info, Exception e) { }

			@Override
			public LifecycleConfigurationUpdate managerShouldUpdateLifecycle(Language language) {
				String appName;
				switch (language) {
					case ES_MX:
						appName = APP_NAME_ES;
						break;
					case FR_CA:
						appName = APP_NAME_FR;
						break;
					default:
						return null;
				}

				return new LifecycleConfigurationUpdate(appName, null, TTSChunkFactory.createSimpleTTSChunks(appName), null);
			}
		};

		// The manager builder sets options for your session
		SdlManager.Builder builder = new SdlManager.Builder(this, appId, APP_NAME + appId, listener);
		localSdlManager = builder.setAppTypes(appType)
				.setTransportType(
						TransportConfigHolder.getInstance().provide(
								this,
								BuildConfig.TRANSPORT,
								BuildConfig.SECURITY,
								appId,
								TCP_PORT,
								DEV_MACHINE_IP_ADDRESS
						)
				)
				.setAppIcon(appIcon)
				.build();

		return localSdlManager;
	}

	/**
	 * Send some voice commands
	 */
	private void setVoiceCommands(SdlManager sdlManager){

		List<String> list1 = Collections.singletonList("Command One");
		List<String> list2 = Collections.singletonList("Command two");

		VoiceCommand voiceCommand1 = new VoiceCommand(list1, new VoiceCommandSelectionListener() {
			@Override
			public void onVoiceCommandSelected() {
				Log.i(TAG, "Voice Command 1 triggered");
			}
		});

		VoiceCommand voiceCommand2 = new VoiceCommand(list2, new VoiceCommandSelectionListener() {
			@Override
			public void onVoiceCommandSelected() {
				Log.i(TAG, "Voice Command 2 triggered");
			}
		});

		sdlManager.getScreenManager().setVoiceCommands(Arrays.asList(voiceCommand1,voiceCommand2));
	}

	/**
	 *  Add menus for the app on SDL.
	 */
	private void sendMenus(final SdlManager sdlManager){

		// some arts
		SdlArtwork livio = new SdlArtwork("livio", FileType.GRAPHIC_PNG, R.drawable.sdl, false);

		// some voice commands
		List<String> voice2 = Collections.singletonList("Cell two");

		MenuCell mainCell1 = new MenuCell("Test Cell 1 (speak)", livio, null, new MenuSelectionListener() {
			@Override
			public void onTriggered(TriggerSource trigger) {
				Log.i(TAG, "Test cell 1 triggered. Source: "+ trigger.toString());
				showTest(sdlManager);
			}
		});

		MenuCell mainCell2 = new MenuCell("Test Cell 2", null, voice2, new MenuSelectionListener() {
			@Override
			public void onTriggered(TriggerSource trigger) {
				Log.i(TAG, "Test cell 2 triggered. Source: "+ trigger.toString());
			}
		});

		// SUB MENU

		MenuCell subCell1 = new MenuCell("SubCell 1",null, null, new MenuSelectionListener() {
			@Override
			public void onTriggered(TriggerSource trigger) {
				Log.i(TAG, "Sub cell 1 triggered. Source: "+ trigger.toString());
			}
		});

		MenuCell subCell2 = new MenuCell("SubCell 2",null, null, new MenuSelectionListener() {
			@Override
			public void onTriggered(TriggerSource trigger) {
				Log.i(TAG, "Sub cell 2 triggered. Source: "+ trigger.toString());
			}
		});

		// sub menu parent cell
		MenuCell mainCell3 = new MenuCell("Test Cell 3 (sub menu)", MenuLayout.LIST, null, Arrays.asList(subCell1,subCell2));

		MenuCell mainCell4 = new MenuCell("Show Perform Interaction", null, null, new MenuSelectionListener() {
			@Override
			public void onTriggered(TriggerSource trigger) {
				showPerformInteraction(sdlManager);
			}
		});

		MenuCell mainCell5 = new MenuCell("Clear the menu",null, null, new MenuSelectionListener() {
			@Override
			public void onTriggered(TriggerSource trigger) {
				Log.i(TAG, "Clearing Menu. Source: "+ trigger.toString());
				// Clear this thing
				sdlManager.getScreenManager().setMenu(Collections.<MenuCell>emptyList());
				showAlert("Menu Cleared", sdlManager);
			}
		});

		// Send the entire menu off to be created
		sdlManager.getScreenManager().setMenu(Arrays.asList(mainCell1, mainCell2, mainCell3, mainCell4, mainCell5));
	}

	/**
	 * Will speak a sample welcome message
	 */
	private void performWelcomeSpeak(SdlManager sdlManager){
		sdlManager.sendRPC(new Speak(TTSChunkFactory.createSimpleTTSChunks(WELCOME_SPEAK)));
	}

	/**
	 * Use the Screen Manager to set the initial screen text and set the image.
	 * Because we are setting multiple items, we will call beginTransaction() first,
	 * and finish with commit() when we are done.
	 */
	private void performWelcomeShow(SdlManager sdlManager) {
		sdlManager.getScreenManager().beginTransaction();
		sdlManager.getScreenManager().setTextField1(APP_NAME);
		sdlManager.getScreenManager().setTextField2(WELCOME_SHOW);
		sdlManager.getScreenManager().setPrimaryGraphic(new SdlArtwork(SDL_IMAGE_FILENAME, FileType.GRAPHIC_PNG, R.drawable.sdl, true));
		sdlManager.getScreenManager().commit(new CompletionListener() {
			@Override
			public void onComplete(boolean success) {
				if (success){
					Log.i(TAG, "welcome show successful");
				}
			}
		});
	}

	/**
	 * Will show a sample test message on screen as well as speak a sample test message
	 */
	private void showTest(SdlManager sdlManager){
		sdlManager.getScreenManager().beginTransaction();
		sdlManager.getScreenManager().setTextField1("Test Cell 1 has been selected");
		sdlManager.getScreenManager().setTextField2("");
		sdlManager.getScreenManager().commit(null);

		sdlManager.sendRPC(new Speak(TTSChunkFactory.createSimpleTTSChunks(TEST_COMMAND_NAME)));
	}

	private void showAlert(String text, SdlManager sdlManager){
		Alert alert = new Alert();
		alert.setAlertText1(text);
		alert.setDuration(5000);
		sdlManager.sendRPC(alert);
	}

	// Choice Set

	private void preloadChoices(SdlManager sdlManager){
		ChoiceCell cell1 = new ChoiceCell("Item 1");
		ChoiceCell cell2 = new ChoiceCell("Item 2");
		ChoiceCell cell3 = new ChoiceCell("Item 3");
		List<ChoiceCell> choiceCellList = new ArrayList<>(Arrays.asList(cell1, cell2, cell3));
		sdlManager.getScreenManager().preloadChoices(choiceCellList, null);
	}

	private void showPerformInteraction(final SdlManager sdlManager){
		List<ChoiceCell> choiceList = new ArrayList<>(sdlManager.getScreenManager().getPreloadedChoices());
		if (!choiceList.isEmpty()) {
			ChoiceSet choiceSet = new ChoiceSet("Choose an Item from the list", choiceList, new ChoiceSetSelectionListener() {
				@Override
				public void onChoiceSelected(ChoiceCell choiceCell, TriggerSource triggerSource, int rowIndex) {
					showAlert(choiceCell.getText() + " was selected", sdlManager);
				}

				@Override
				public void onError(String error) {
					Log.e(TAG, "There was an error showing the perform interaction: "+ error);
				}
			});
			sdlManager.getScreenManager().presentChoiceSet(choiceSet, InteractionMode.MANUAL_ONLY);
		}
	}
}
