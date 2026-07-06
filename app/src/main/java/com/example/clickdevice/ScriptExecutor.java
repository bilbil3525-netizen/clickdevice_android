package com.example.clickdevice;

import android.util.Log;

import com.example.clickdevice.bean.ScriptCmdBean;

import java.util.List;
import java.util.Stack;

public class ScriptExecutor {
    private static final String TAG = "ScriptExecutor";
    private ScriptInterFace scriptInterFace;
    private Stack<ForStart> stack = new Stack<>();

    public ScriptExecutor(ScriptInterFace scriptInterFace) {
        this.scriptInterFace = scriptInterFace;
    }

    public ScriptInterFace getScriptInterFace() {
        return scriptInterFace;
    }

    public void setScriptInterFace(ScriptInterFace scriptInterFace) {
        this.scriptInterFace = scriptInterFace;
    }


    public void run(List<ScriptCmdBean> list) {
        try {
            int size = list.size();
            boolean isJump = false;
            int loopIteration = 0;
            for (int i = 0; i < size; i++) {
                if (scriptInterFace==null||!scriptInterFace.isRun()){
                    logW("run(List) aborted: isRun=false at i=" + i);
                    return;
                }
                ScriptCmdBean scriptCmdBean = list.get(i);
                if (scriptCmdBean.getAction() == ScriptCmdBean.ACTION_FOR_END) {
                    isJump = false;
                    if (!stack.empty()) {
                        ForStart forStart = stack.pop();
                        if (forStart.num > 0) {
                            loopIteration++;
                            logD("FOR loop iteration #" + loopIteration + " (remaining=" + forStart.num + ")");
                            i = forStart.index;
                            forStart.num--;
                            stack.push(forStart);
                        }
                    }
                } else {
                    if (isJump) {
                        continue;
                    }
                    if (scriptCmdBean.getAction() == ScriptCmdBean.ACTION_FOR) {
                        int f = scriptCmdBean.getFrequency();
                        logD("FOR start: frequency=" + f + " at index=" + i);
                        if (f == 0) {
                            isJump = true;
                            logD("FOR: frequency=0, infinite loop");
                            continue;
                        }
                        ForStart forStart = new ForStart(i, --f);
                        stack.push(forStart);
                    } else {
                        try {
                            run(scriptCmdBean);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

        } catch (Throwable e) {

        }

    }

    public void run(ScriptCmdBean scriptCmdBean) throws InterruptedException {
        if (scriptCmdBean == null || scriptInterFace == null) {
            logW("run(ScriptCmdBean): null cmd or interface");
            return;
        }
        if (scriptCmdBean.getAction() == ScriptCmdBean.ACTION_DELAYED) {
            logD("DELAY " + scriptCmdBean.getDelayed() + "ms");
            scriptInterFace.delayedCmd(scriptCmdBean.getDelayed());
        } else if (scriptCmdBean.getAction() == ScriptCmdBean.ACTION_CLICK) {
            int repeatCount = Math.max(scriptCmdBean.getRepeatCount(), 1);
            logD("CLICK (" + scriptCmdBean.getX0() + "," + scriptCmdBean.getY0()
                + ") x" + repeatCount + " duration=" + scriptCmdBean.getDuration() + "ms");
            for (int r = 0; r < repeatCount; r++) {
                if (scriptInterFace == null || !scriptInterFace.isRun()) {
                    logW("CLICK aborted at repeat " + r + "/" + repeatCount);
                    return;
                }
                logD("CLICK repeat " + (r+1) + "/" + repeatCount);
                scriptInterFace.delayedCmd(scriptCmdBean.getDelayed());
                scriptInterFace.clickCMD(scriptCmdBean.getX0(), scriptCmdBean.getY0(), scriptCmdBean.getDuration());
                if (r < repeatCount - 1) {
                    Thread.sleep(80);
                }
            }
        } else if (scriptCmdBean.getAction() == ScriptCmdBean.ACTION_GESTURE) {
            int repeatCount = Math.max(scriptCmdBean.getRepeatCount(), 1);
            logD("GESTURE (" + scriptCmdBean.getX0() + "," + scriptCmdBean.getY0()
                + ")→(" + scriptCmdBean.getX1() + "," + scriptCmdBean.getY1()
                + ") x" + repeatCount + " duration=" + scriptCmdBean.getDuration() + "ms");
            for (int r = 0; r < repeatCount; r++) {
                if (scriptInterFace == null || !scriptInterFace.isRun()) {
                    logW("GESTURE aborted at repeat " + r + "/" + repeatCount);
                    return;
                }
                logD("GESTURE repeat " + (r+1) + "/" + repeatCount);
                scriptInterFace.delayedCmd(scriptCmdBean.getDelayed());
                scriptInterFace.gestureCMD(scriptCmdBean.getX0(), scriptCmdBean.getY0()
                        , scriptCmdBean.getX1(), scriptCmdBean.getY1()
                        , scriptCmdBean.getDuration());
                if (r < repeatCount - 1) {
                    Thread.sleep(80);
                }
            }
        } else if (scriptCmdBean.getAction() == ScriptCmdBean.ACTION_RANDOM_CLICK) {
            int repeatCount = Math.max(scriptCmdBean.getRepeatCount(), 1);
            logD("RANDOM_CLICK (" + scriptCmdBean.getX0() + "," + scriptCmdBean.getY0()
                + ")~(" + scriptCmdBean.getX1() + "," + scriptCmdBean.getY1()
                + ") x" + repeatCount);
            for (int r = 0; r < repeatCount; r++) {
                if (scriptInterFace == null || !scriptInterFace.isRun()) {
                    logW("RANDOM_CLICK aborted at repeat " + r + "/" + repeatCount);
                    return;
                }
                scriptInterFace.delayedCmd(scriptCmdBean.getDelayed());
                int x = Util.randomInt(scriptCmdBean.getX1(), scriptCmdBean.getX0());
                int y = Util.randomInt(scriptCmdBean.getY1(), scriptCmdBean.getY0());
                scriptInterFace.clickCMD(x, y, scriptCmdBean.getDuration());
                if (r < repeatCount - 1) {
                    Thread.sleep(80);
                }
            }
        }
    }

    private static void logD(String message) {
        try {
            Log.d(TAG, message);
        } catch (RuntimeException ignored) {
        }
    }

    private static void logW(String message) {
        try {
            Log.w(TAG, message);
        } catch (RuntimeException ignored) {
        }
    }

    private class ForStart {
        private int index;
        private int num;

        public ForStart(int index, int num) {
            this.index = index;
            this.num = num;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public int getNum() {
            return num;
        }

        public void setNum(int num) {
            this.num = num;
        }
    }

    public interface ScriptInterFace {

        boolean isRun();

        void delayedCmd(int delayed) throws InterruptedException;

        void clickCMD(int x0, int y0, int duration) throws InterruptedException;

        void gestureCMD(int x0, int y0, int x1, int y1, int duration) throws InterruptedException;

    }

}
