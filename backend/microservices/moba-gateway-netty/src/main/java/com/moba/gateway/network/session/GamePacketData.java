package com.moba.gateway.network.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GamePacketData {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final int cmd;
    private final int seq;
    private final int ver;
    private final int st;
    private final Object data;

    public GamePacketData(int cmd, int seq, int ver, int st, Object data) {
        this.cmd = cmd;
        this.seq = seq;
        this.ver = ver;
        this.st = st;
        this.data = data;
    }

    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{\"cmd\":" + cmd + ",\"seq\":" + seq + ",\"ver\":" + ver + ",\"st\":" + st + ",\"data\":null}";
        }
    }

    public int getCmd() { return cmd; }
    public int getSeq() { return seq; }
    public int getVer() { return ver; }
    public int getSt() { return st; }
    public Object getData() { return data; }
}
