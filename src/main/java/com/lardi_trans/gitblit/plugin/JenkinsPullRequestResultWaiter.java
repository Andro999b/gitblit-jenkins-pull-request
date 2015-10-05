package com.lardi_trans.gitblit.plugin;

import com.gitblit.GitBlit;
import com.gitblit.models.TicketModel;
import com.gitblit.servlet.GitblitContext;
import com.gitblit.tickets.ITicketService;
import com.lardi_trans.gitblit.plugin.api.JenkinsApi;
import com.lardi_trans.gitblit.plugin.api.JsonApiFactory;
import com.lardi_trans.gitblit.plugin.model.JenkinsResult;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static com.jayway.jsonpath.Criteria.where;
import static com.jayway.jsonpath.Filter.filter;

/**
 * Created by Andrey
 * 17.02.2015.
 */
public class JenkinsPullRequestResultWaiter implements Runnable {

    public static final int MINUTE_IN_MULLLIS = 60000;
    private final TicketModel.Patchset patchset;
    private final String jenkinsJob;
    private final String repositoryName;
    private final TicketModel ticketModel;
    private final JenkinsApi jenkinsApi;
    private boolean finish = false;
    private int errorsCount;
    private long startTimeStamp;
    private JenkinsResult jobResult;

    public JenkinsPullRequestResultWaiter(
            String repositoryName,
            TicketModel ticketModel,
            TicketModel.Patchset patchset,
            String jenkinsJob) {

        this.patchset = patchset;
        this.jenkinsJob = jenkinsJob;
        this.repositoryName = repositoryName;
        this.ticketModel = ticketModel;
        this.jenkinsApi = JsonApiFactory.newInstance();
    }

    @Override
    public void run() {
        while (!finish) {
            try {
                if (jobResult == null) {
                    JenkinsResult lastResult = jenkinsApi.getLastResult(jenkinsJob);
                    if (checkTicket(lastResult)) {
                        jobResult = lastResult;
                    }
                } else {
                    jobResult = jenkinsApi.updateResult(jobResult);
                }

                if (!jobResult.building) {
                    if (jobResult.success) {
                        postComment(getSuccessMessage());
                    } else {
                        postComment(getErrorMessage());
                    }
                }
            } catch (Exception ex) {
                errorsCount++;
                LoggerFactory.getLogger(JenkinsPullRequestTicketHook.class).error("Error while getting result of ticket job result " + jenkinsJob + "", ex);
            }

            if (errorsCount > JenkinsPullRequestUtils.MAX_WAITER_ERRORS) {
                finish = true;
            }

            long currentRequestTimeStamp = System.currentTimeMillis();

            if (startTimeStamp != 0) {
                if (currentRequestTimeStamp - startTimeStamp > JenkinsPullRequestUtils.MAX_WAITER_TIME * MINUTE_IN_MULLLIS) {
                    finish = true;
                }
            } else {
                startTimeStamp = currentRequestTimeStamp;
            }

            if (finish) {
                try {
                    TimeUnit.SECONDS.sleep(JenkinsPullRequestUtils.WAIT_TIMEOUT);
                } catch (InterruptedException e) {
                    //nothing
                }
            }
        }
    }

    private String getErrorMessage() {
        return "<span style=\"color:#B0171F\"><b>fail to build patchset: " + patchset.rev + "</b></span>";
    }

    private String getSuccessMessage() {
        return "<span style=\"color:green\"><b>success to build patchset: " + patchset.rev + "</b></span>";
    }

    private boolean checkTicket(JenkinsResult result) {
        String ticketBranchName = JenkinsPullRequestUtils.getTicketBranchName(ticketModel.number);

        boolean thisTicket = result.refs.stream().anyMatch(ref -> ref.endsWith(ticketBranchName));
        boolean thisRemote = result.remotes.stream().anyMatch(remote -> remote.endsWith(repositoryName));

        return thisTicket && thisRemote;
    }

    private void postComment(String message) {
        GitBlit gitblit = GitblitContext.getManager(GitBlit.class);

        ITicketService ticketService = gitblit.getTicketService();

        TicketModel.Change comment = new TicketModel.Change("jenkins");
        comment.comment(message);
        comment.review(patchset, TicketModel.Score.needs_improvement, true);

        ticketService.updateTicket(gitblit.getRepositoryModel(repositoryName), ticketModel.number, comment);
    }
}
