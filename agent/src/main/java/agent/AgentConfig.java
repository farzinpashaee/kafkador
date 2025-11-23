package agent;

public class AgentConfig {

    private String endpoint = "http://localhost:8080/api/apm/metric/ingest";
    private Integer period = 30;
    private String clusterId;
    private String brokerId;

    public AgentConfig( String agentArgs ){
        if (agentArgs != null && !agentArgs.isEmpty()) {
            String[] args = agentArgs.split(",");

            try {
                this.endpoint = (args[0] != null && !args[0].isBlank())  ? args[0] : this.endpoint;
            } catch (Exception ex){
                System.out.println("endpoint set to default :" + this.endpoint);
            }

            try {
                this.period = (args[1] != null && !args[1].isBlank())  ? Integer.valueOf(args[1]) : this.period;
            } catch (Exception ex){
                System.out.println("period set to default :" + this.period);
            }

            try {
                this.clusterId = (args[2] != null && !args[2].isBlank())  ? args[2] : this.clusterId;
            } catch (Exception ex){
                System.out.println("clusterId not set!");
            }

            try {
                this.brokerId = (args[3] != null && !args[3].isBlank())  ? args[3] : this.brokerId;
            } catch (Exception ex){
                System.out.println("brokerId not set!");
            }

        }
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Integer getPeriod() {
        return period;
    }

    public void setPeriod(Integer period) {
        this.period = period;
    }

    public String getClusterId() {
        return clusterId;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    public String getBrokerId() {
        return brokerId;
    }

    public void setBrokerId(String brokerId) {
        this.brokerId = brokerId;
    }
}
