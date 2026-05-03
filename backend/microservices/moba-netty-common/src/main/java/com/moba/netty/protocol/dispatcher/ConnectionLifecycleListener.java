package com.moba.netty.protocol.dispatcher;

import com.moba.netty.session.Session;

public interface ConnectionLifecycleListener {

    default void onSessionActive(Session session) {}

    default void onSessionInactive(Session session) {}
}
