package com.lardi_trans.gitblit.plugin.model;

import java.util.List;

/**
 * Created by Andrey on 17.02.2015.
 */
public class JenkinsResult {
    public List<String> refs;
    public List<String> remotes;
    public boolean building;
    public boolean success;
    public String url;
}
