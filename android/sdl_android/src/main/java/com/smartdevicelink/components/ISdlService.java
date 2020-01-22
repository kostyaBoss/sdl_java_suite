package com.smartdevicelink.components;

import android.support.annotation.DrawableRes;

public interface ISdlService {

    Integer provideServiceForegroundId();

    String provideServiceName();

    @DrawableRes
    Integer provideServiceIcon();

    String provideServiceNotificationTitle();

    void configure();

    void disposeSdlManagers();
}
