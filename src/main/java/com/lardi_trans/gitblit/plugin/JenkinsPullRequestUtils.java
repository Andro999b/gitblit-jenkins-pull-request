package com.lardi_trans.gitblit.plugin;

import com.gitblit.GitBlit;

/**
 * Created by Andrey on 17.02.2015.
 */
public class JenkinsPullRequestUtils {
    public final static int MAX_WAITER_ERRORS = 10;
    public final static int MAX_WAITER_TIME = 60;//minutes
    public final static int WAIT_TIMEOUT = 5;

    public static String getTicketBranchName(long ticketNumber) {
        return "ticket/" + ticketNumber;
    }
}
