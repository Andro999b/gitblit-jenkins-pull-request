package com.lardi_trans.gitblit.plugin.api;

import com.gitblit.GitBlit;
import com.gitblit.servlet.GitblitContext;

/**
 * Created by Andrey
 * 21.02.2015
 */
public class JsonApiFactory {
    public static JenkinsApi newInstance() {
        GitBlit gitblit = GitblitContext.getManager(GitBlit.class);
        String url = gitblit.getString("jenkins.server", "http://yourserver/jenkins");
        String token = gitblit.getString("jenkins.token", "");
        String jobsList = gitblit.getString("jenkins.jobsList", "");

        return new JenkinsJsonApiImpl(url, token, jobsList);
    }
}
