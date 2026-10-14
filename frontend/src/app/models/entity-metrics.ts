/** A Confluent metrics topic Kafkador reads, and what came of reading it. */
export interface MetricSource {
    topic: string;
    exists: boolean;
    scannedRecords: number;
    undecodedRecords: number;
    matchedSamples: number;
    fromTimestamp?: number;
    toTimestamp?: number;
    error?: string;
}

export interface MetricPoint {
    t: number;
    v: number;
}

/** One metric with one set of labels, its recent values oldest first. */
export interface MetricSeries {
    name: string;
    source: string;
    /** gauge, counter, rate, count or mean. */
    kind: string;
    broker?: string;
    labels: Record<string, string>;
    latest: number;
    latestTimestamp: number;
    points: MetricPoint[];
}

/** Recent metrics of one broker or topic, read from the Confluent metrics topics. */
export interface EntityMetrics {
    sources: MetricSource[];
    series: MetricSeries[];
    seriesLimitReached: boolean;
}
