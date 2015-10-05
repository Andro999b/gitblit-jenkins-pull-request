package com.lardi_trans.gitblit.plugin.api;

import com.gitblit.utils.StringUtils;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.Filter;
import com.jayway.jsonpath.JsonPath;
import com.lardi_trans.gitblit.plugin.JenkinsPullRequestTicketHook;
import com.lardi_trans.gitblit.plugin.model.JenkinsResult;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import static com.jayway.jsonpath.Criteria.where;
import static com.jayway.jsonpath.Filter.filter;

public class JenkinsJsonApiImpl implements JenkinsApi {
    private String url;
    private String pullRequestJobs;
    private String token;

    public JenkinsJsonApiImpl(String url, String pullRequestJobs) {
        this(url, pullRequestJobs, "");
    }

    public JenkinsJsonApiImpl(String url, String pullRequestJobs, String token) {
        this.url = url;
        this.pullRequestJobs = pullRequestJobs;
        this.token = token;
    }

    @Override
    public void trigerJob(String jobName, String branch) {
        String jenkinsJobUrl = url + "/job/" + jobName;
               String params = "branch=" + branch;
        if (!token.isEmpty()) {
            params += "&token=" + token;
        }

        try {
            new URL(jenkinsJobUrl + "/buildWithParameters?delay=0sec&" + params).openConnection().getInputStream();
        } catch (IOException e) {
            throw new RuntimeException("Error to run jenkins ticket job " + jobName, e);
        }
    }

    @Override
    public JenkinsResult getLastResult(String jobName) {
        return getResult(url + "/job/" + jobName + "/lastBuild/" + "api/json/");
    }

    @Override
    public JenkinsResult updateResult(JenkinsResult oldResult) {
        if(StringUtils.isEmpty(oldResult.url))
            return null;

        return getResult(oldResult.url);
    }

    private JenkinsResult getResult(String jobUrl){
        InputStream stream = null;
        try {
            stream = new URL(jobUrl).openStream();
        } catch (IOException e) {
            throw new RuntimeException("Fail to get job result " + jobUrl, e);
        }

        DocumentContext cxt = JsonPath.parse(stream);

        JenkinsResult jenkinsJesult = new JenkinsResult();
        Filter actionsFilter = filter(where("remoteUrls").exists(true));
        Filter refFilter = filter(where("remoteUrls").exists(true));
        jenkinsJesult.remotes = cxt.read("$.actions[?].remoteUrls[0]", actionsFilter);
        jenkinsJesult.refs = cxt.read("$.actions[*].lastBuiltRevision.branch[*].name", actionsFilter, refFilter);
        jenkinsJesult.building = cxt.read("$.building");
        jenkinsJesult.url = cxt.read("$.url");

        String result = cxt.read("$.result");

        if (result != null) {
            jenkinsJesult.success = result.equals("SUCCESS");
        }

        return jenkinsJesult;
    }

    @Override
    public List<String> getPullRequestJobs() {
        try {
            InputStream stream = new URL(url + "/view/" + pullRequestJobs + "/api/json").openStream();
            List<String> availableJobs = JsonPath.read(stream, "$.jobs[*].name");
            return availableJobs;
        } catch (Exception ex) {
            LoggerFactory.getLogger(JenkinsPullRequestTicketHook.class).error("Error while getting list of git-tickets-jobs from jenkins", ex);
        }
        return Collections.emptyList();
    }
}
