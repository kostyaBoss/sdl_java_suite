package com.smartdevicelink.managers.video.resolution;

public class VideoStreamingRange {
    private Resolution minSupportedResolution;
    private Resolution maxSupportedResolution;
    private Double maxScreenDiagonal;
    private AspectRatio aspectRatio;

    public VideoStreamingRange(Resolution minSupportedResolution, Resolution maxSupportedResolution, Double maxScreenDiagonal, AspectRatio aspectRatio) {
        this.minSupportedResolution = minSupportedResolution;
        this.maxSupportedResolution = maxSupportedResolution;
        this.maxScreenDiagonal = maxScreenDiagonal;
        this.aspectRatio = aspectRatio;
    }

    private VideoStreamingRange(){}

    public Resolution getMinSupportedResolution() {
        return minSupportedResolution;
    }

    public Resolution getMaxSupportedResolution() {
        return maxSupportedResolution;
    }

    public Double getMaxScreenDiagonal() {
        return maxScreenDiagonal;
    }

    public AspectRatio getAspectRatio() {
        return aspectRatio;
    }

    public static class Builder {
        private VideoStreamingRange range = new VideoStreamingRange();

        public Builder setMinSupportedResolution(Resolution minSupportedResolution) {
            range.minSupportedResolution = minSupportedResolution;
            return this;
        }

        public Builder setMaxSupportedResolution(Resolution maxSupportedResolution) {
            range.maxSupportedResolution = maxSupportedResolution;
            return this;
        }

        public Builder setMaxScreenDiagonal(Double maxScreenDiagonal) {
            range.maxScreenDiagonal = maxScreenDiagonal;
            return this;
        }

        public Builder setAspectRatio(AspectRatio aspectRatio){
            range.aspectRatio = aspectRatio;
            return this;
        }

        public VideoStreamingRange build(){
            return range;
        }
    }
}