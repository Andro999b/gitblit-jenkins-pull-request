package com.lardi_trans.gitblit.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitblit.GitBlit;
import com.gitblit.extensions.TicketHook;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.*;
import com.gitblit.servlet.GitblitContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;

import org.slf4j.LoggerFactory;
import ro.fortsoft.pf4j.Extension;

/**
 *
 * @author andro
 */
@Extension
public class JenkinsPullRequestTicketHook extends TicketHook {
    private final ExecutorService executorService;

    public JenkinsPullRequestTicketHook() {
        executorService = Executors.newFixedThreadPool(5);
    }

    @Override
    public void onNewTicket(TicketModel ticket) {
        requestMergeTicket(ticket);
    }

    @Override
    public void onUpdateTicket(TicketModel ticket, Change change) {
        if (ticket.isOpen()) {
            requestMergeTicket(ticket);
        }
    }

    private void requestMergeTicket(TicketModel ticket) {
        if(!ticket.hasPatchsets()) // нечего мерджить
            return;

        String branch = getTicketBranchName(ticket);

        GitBlit gitblit = GitblitContext.getManager(GitBlit.class);

        String jenkinsUrl = gitblit.getString("jenkins.server", "http://yourserver/jenkins");
        String jenkinsToken = gitblit.getString("jenkins.token", "");
        String ticketsJobsListName = gitblit.getString("jenkins.ticketsJobsList", "git-tickets-jobs");
        
        List<String> ticketsJobsList = getTicketsJobs(jenkinsUrl, ticketsJobsListName);
        
        String jobName = getJobName(ticket.repository);
        
        if(ticketsJobsList.contains(jobName)){
            try {
                String jenkinsJobUrl = jenkinsUrl + "/view/" + ticketsJobsListName + "/job/" + jobName;
                String params = "branch=" + branch;
                
                if(!jenkinsToken.isEmpty())
                    params += "&token=" + jenkinsToken;
                
                new URL(jenkinsJobUrl + "/buildWithParameters?delay=0sec&" + params).openConnection().getInputStream();

                waitForResult(ticket, jobName);
            } catch (IOException ex) {
                LoggerFactory.getLogger(JenkinsPullRequestTicketHook.class)
                    .error("Error to run jenkins merge job for " + ticket.repository, ex);
            }
        }
    }

    private void waitForResult(TicketModel ticket, String jobName) {
        executorService.execute(new ResultWaiter(ticket.number, jobName, ticket.repository));
    }

    private String getJobName(String repositoryName) {
        return repositoryName.replace("/", "-");
    }
    
    private String getTicketBranchName(TicketModel ticket) {
        return "ticket/" + ticket.number;
    }

    private List<String> getTicketsJobs(String jenkinsUrl, String gitMergeJobsList) {

        try {
            InputStream openStream = new URL(jenkinsUrl + "/view/" +gitMergeJobsList+ "/api/json").openStream();
            ObjectMapper mapper = new ObjectMapper();
            
            JsonNode root = mapper.readTree(openStream);
            Iterator<JsonNode> elements = root.get("jobs").elements();
            
            List<String> availableJobs = new ArrayList<>();
            
            while (elements.hasNext()) {
                JsonNode next = elements.next();
                availableJobs.add(next.get("name").asText());
            }
            
            return availableJobs;
        } catch (Exception ex) {
            LoggerFactory.getLogger(JenkinsPullRequestTicketHook.class)
                    .error("Error while getting list on git-tickets-jobs from jenkins", ex);
        }

        return Collections.emptyList();
    }

    private class ResultWaiter implements Runnable {
        private long ticketNumber;
        private String repository;
        private String jenkinsJob;
        private long jenkinsJobNumber;

        public ResultWaiter(long ticketNumber, String jenkinsJob, String repository) {
            this.ticketNumber = ticketNumber;
            this.jenkinsJob = jenkinsJob;
            this.repository = repository;
        }

        @Override
        public void run() {

        }
    }

}
