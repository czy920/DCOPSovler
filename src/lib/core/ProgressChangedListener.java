package lib.core;

import lib.result.Result;

public interface ProgressChangedListener {
    void onProgressChanged(double percentage, Result result);
    void interrupted(String reason);
}
