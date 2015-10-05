package com.lardi_trans.gitblit.plugin;

import com.gitblit.GitBlit;
import com.gitblit.extensions.TicketHook;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.*;
import com.gitblit.servlet.GitblitContext;
import com.gitblit.utils.StringUtils;

import java.util.List;
import java.util.concurrent.*;

import static com.jayway.jsonpath.Criteria.where;

import com.lardi_trans.gitblit.plugin.api.JenkinsApi;
import com.lardi_trans.gitblit.plugin.api.JsonApiFactory;
import org.slf4j.LoggerFactory;
import ro.fortsoft.pf4j.Extension;

/**
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
        if (!ticket.hasPatchsets()) // нечего мерджить
            return;

        String branch = JenkinsPullRequestUtils.getTicketBranchName(ticket.number);

        JenkinsApi jenkinsApi = JsonApiFactory.newInstance();
        String jobName = getJobNameFromRepositoryCustomFields(ticket.repository);

        if (StringUtils.isEmpty(jobName)) {
            jobName = getJobNameFromJenkinsAvailableJobsList(jenkinsApi, ticket.repository);
        }

        if (!StringUtils.isEmpty(jobName)) {
            try {
                jenkinsApi.trigerJob(jobName, branch);
                waitForResult(ticket, jobName);
            } catch (Exception e) {
                LoggerFactory.getLogger(JenkinsPullRequestTicketHook.class)
                        .error("Error to run jenkins ticket job for " + ticket.repository, e);
            }
        }
    }

    private String getJobNameFromRepositoryCustomFields(String repositoryName) {
        GitBlit gitblit = GitblitContext.getManager(GitBlit.class);
        RepositoryModel repositoryModel = gitblit.getRepositoryModel(repositoryName);
        return repositoryModel.customFields.get("jenkinsTicketsJob");
    }

    private String getJobNameFromJenkinsAvailableJobsList(JenkinsApi jenkinsApi, String repositoryName) {
        List<String> pullRequestJobs = jenkinsApi.getPullRequestJobs();
        String jobName = repositoryName.replace("/", "-");

        if (pullRequestJobs.contains(jobName)) {
            return jobName;
        }

        return null;
    }

    private void waitForResult(TicketModel ticket, String jobName) {
        executorService.execute(new JenkinsPullRequestResultWaiter(ticket.repository, ticket, ticket.getCurrentPatchset(), jobName));
    }
}
