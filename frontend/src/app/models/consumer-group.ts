export class ConsumerGroup {
    id!: string;
    type!: string;
    coordinator!: string;
    groupState!: string;
    partitionAssignor!: string;
    isSimpleConsumerGroup!: boolean;
}
