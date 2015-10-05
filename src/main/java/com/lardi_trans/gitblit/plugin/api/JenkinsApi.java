package com.lardi_trans.gitblit.plugin.api;

import com.lardi_trans.gitblit.plugin.model.JenkinsResult;

import java.util.List;

/**
 * Created by Andrey on 17.02.2015.
 */
public interface JenkinsApi {
    void trigerJob(String jobName, String branch);
    JenkinsResult getLastResult(String jobName);
    JenkinsResult updateResult(JenkinsResult oldResult);
    List<String> getPullRequestJobs();
}
