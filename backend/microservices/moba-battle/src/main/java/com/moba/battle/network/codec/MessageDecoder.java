package com.moba.battle.network.codec;

import com.moba.netty.codec.PacketDecoder;

public class MessageDecoder extends PacketDecoder {

    public MessageDecoder(int maxFrameLength) {
        super(maxFrameLength);
    }
}
