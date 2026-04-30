package com.moba.common.event;

import com.moba.common.dto.BattleResultDTO;
import lombok.Data;

import java.io.Serializable;

@Data
public class BattleEndEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String eventId;
    private long timestamp;
    private BattleResultDTO result;
    private byte[] replayFrameData;
    private byte[] replaySnapshotData;
}
