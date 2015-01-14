package com.lardi_trans.gitblit.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitblit.GitBlit;
import com.gitblit.extensions.TicketHook;
import com.gitblit.git.PatchsetCommand;
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
import org.slf4j.LoggerFactory;
import ro.fortsoft.pf4j.Extension;

/**
 *
 * @author andro
 */
@Extension
public class JenkinsPullRequestTicketHook extends TicketHook {

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
        GitBlit gitblit = GitblitContext.getManager(GitBlit.class);

        String branch = PatchsetCommand.getTicketBranch(ticket.number);
        
        String jenkinsUrl = gitblit.getString("jenkins.server", "http://yourserver/jenkins");
        String jenkinsToken = gitblit.getString("jenkins.token", "");
        String mergeJobsList = gitblit.getString("jenkins.mergeJobsList", "git-merg-jobs");
        
        List<String> mergersList = getMergersList(jenkinsUrl, mergeJobsList);
        
        if(mergersList.contains(ticket.repository)){
            try {
                String jenkinsJobUrl = jenkinsUrl + "/view/" + mergeJobsList + "/job/" + ticket.repository;
                String params = "branch=" + branch;
                
                if(!jenkinsToken.isEmpty())
                    params += "&token=" + jenkinsToken;
                
                new URL(jenkinsJobUrl + "/buildWithParameters?delay=0sec&" + params).getContent();
            } catch (IOException ex) {
                LoggerFactory.getLogger(JenkinsPullRequestTicketHook.class)
                    .error("Error to run jenkins merge job for" + ticket.repository, ex);
            }
        }
    }

    private static List<String> getMergersList(String jenkinsUrl, String gitMergeJobsList) {

        try {
            InputStream openStream = new URL(jenkinsUrl + "/view/" +gitMergeJobsList+ "/api/json").openStream();
            ObjectMapper mapper = new ObjectMapper();
            
            JsonNode root = mapper.readTree(openStream);
            Iterator<JsonNode> elements = root.get("jobs").elements();
            
            List<String> avaliableJobs = new ArrayList<>();
            
            while (elements.hasNext()) {
                JsonNode next = elements.next();
                avaliableJobs.add(next.get("name").asText());
            }
            
            return avaliableJobs;
        } catch (Exception ex) {
            LoggerFactory.getLogger(JenkinsPullRequestTicketHook.class)
                    .error("Error while getting list on git-merg-jobs from jenkins", ex);
        }

        return Collections.emptyList();
    }
}
