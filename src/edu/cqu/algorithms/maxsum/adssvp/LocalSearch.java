package edu.cqu.algorithms.maxsum.adssvp;

import edu.cqu.core.Message;

public interface LocalSearch {
    void initRun();
    void disposeMessage(Message message);
    void allMessageDisposed();
    int getValueIndex();
}
