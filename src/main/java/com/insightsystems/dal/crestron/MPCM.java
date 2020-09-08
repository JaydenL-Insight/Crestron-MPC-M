package com.insightsystems.dal.crestron;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.error.CommandFailureException;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.dal.communicator.TelnetCommunicator;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MPCM extends TelnetCommunicator implements Monitorable, Controller {
    private final String queryVersion = "ver", queryUptime = "uptime",queryProgInfo = "proginfo", queryNetworkStatus = "estatus", queryProgramSpace = "free";
    private final String cmdReboot = "reboot";

    public MPCM(){
        this.setCommandErrorList(Collections.singletonList("None"));
        this.setCommandSuccessList(Collections.singletonList(""));
        this.setLoginErrorList(Collections.singletonList("None"));
        this.setLoginSuccessList(Collections.singletonList(""));
    }

    @Override
    protected boolean doneReading(String command, String response) throws CommandFailureException {
        return !regexFind(response,"(MPC-M\\d+>)").isEmpty(); // contains "MPC-M**>"
    }

    @Override
    public List<Statistics> getMultipleStatistics() throws Exception {
        ExtendedStatistics extStats = new ExtendedStatistics();
        Map<String, String> stats = new LinkedHashMap<>();
        List<AdvancedControllableProperty> ctrls = new ArrayList<>();

        send("");

        stats.put("firmwareVersion",regexFind(send(queryVersion),"[\\w\\s-]+\\[([^,]+)"));
        stats.put("uptime",regexFind(send(queryUptime),"(\\d+[^\\r\\n]+)"));
        stats.put("programCompileDate",regexFind(send(queryProgInfo),"Compile Date\\/Time:\\s+(\\d+[^\\r\\n]+)"));
        final String networkStats = send(queryNetworkStatus);
        stats.put("macAddress",regexFind(networkStats,"MAC Address\\(es\\):\\s([\\d.a-fA-F]+)"));
        stats.put("hostName",regexFind(networkStats,"Host Name:\\s+([^\\r\\n]+)"));
        final String freeSpace = regexFind(send(queryProgramSpace),"(\\d+)\\sbytes");
        if (!freeSpace.isEmpty())
            stats.put("storageUsed",((65536F - Float.parseFloat(freeSpace))/65536F)*100F + "%");

        stats.put("reboot","0");
        ctrls.add(new AdvancedControllableProperty("reboot",new Date(),createButton(),"0"));
        extStats.setControllableProperties(ctrls);
        extStats.setStatistics(stats);
        return Collections.singletonList(extStats);
    }

    private AdvancedControllableProperty.ControllableType createButton() {
        AdvancedControllableProperty.Button button = new AdvancedControllableProperty.Button();
        button.setGracePeriod(10000L);
        button.setLabel("Reboot");
        button.setLabelPressed("Rebooting");
        return button;
    }


    @Override
    public void controlProperty(ControllableProperty cp) throws Exception {
        if (cp.getProperty().isEmpty() || cp.getProperty() == null){
            return;
        }

        if (cp.getProperty().equals("reboot")){
            send(cmdReboot);
        } else {
            if (this.logger.isDebugEnabled()){
                this.logger.debug("Control Property: " + cp.getProperty() + " could not be found.");
            }
        }

    }

    @Override
    public void controlProperties(List<ControllableProperty> list) throws Exception {
        for (ControllableProperty cp : list){
            controlProperty(cp);
        }
    }


    private String regexFind(String sourceString,String regex){
        final Matcher matcher = Pattern.compile(regex).matcher(sourceString);
        return matcher.find() ? matcher.group(1) : "";
    }


    public static void main(String[] args) throws Exception {
        MPCM dm = new MPCM();
        dm.setHost("10.193.77.175");
        dm.setPort(41795);
        dm.init();
        ((ExtendedStatistics)dm.getMultipleStatistics().get(0)).getStatistics().forEach((k,v)->System.out.println(k + " : " + v));
    }


}
