package io.logz.sender;

/**
 * Created by MarinaRazumovsky on 15/12/2016.
 */
public interface ILogzioStatusReporter {


    public void error(String msg);

    public void error(String msg, Throwable e);

    public void warning(String msg);

    public void warning(String msg, Throwable e);

    public void info(String msg);

    public void info(String msg, Throwable e);
}
