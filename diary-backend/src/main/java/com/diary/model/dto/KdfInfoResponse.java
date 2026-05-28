package com.diary.model.dto;

import java.util.Map;

public class KdfInfoResponse {

    private KdfInfo current;
    private KdfInfo recommended;

    public KdfInfoResponse(KdfInfo current, KdfInfo recommended) {
        this.current = current;
        this.recommended = recommended;
    }

    public KdfInfo getCurrent() { return current; }
    public KdfInfo getRecommended() { return recommended; }

    public static class KdfInfo {
        private int kdfVersion;
        private Map<String, Object> kdfParams;

        public KdfInfo(int kdfVersion, Map<String, Object> kdfParams) {
            this.kdfVersion = kdfVersion;
            this.kdfParams = kdfParams;
        }

        public int getKdfVersion() { return kdfVersion; }
        public Map<String, Object> getKdfParams() { return kdfParams; }
    }
}
