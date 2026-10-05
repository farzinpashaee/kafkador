package com.csl.kafkador.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClusterServiceImpTest {

    @Test
    void namesKnownMetadataVersionLevels() {
        assertThat(ClusterServiceImp.metadataVersionName(7)).isEqualTo("3.3-IV3");
        assertThat(ClusterServiceImp.metadataVersionName(21)).isEqualTo("3.9-IV0");
        assertThat(ClusterServiceImp.metadataVersionName(27)).isEqualTo("4.1-IV1");
    }

    @Test
    void fallsBackToTheRawLevelForUnknownLevels() {
        assertThat(ClusterServiceImp.metadataVersionName(6)).isEqualTo("metadata.version 6");
        assertThat(ClusterServiceImp.metadataVersionName(99)).isEqualTo("metadata.version 99");
    }
}
