package com.example.clickdevice.bean;

import java.util.List;

public class RecordScriptCmd {

    public Type type;
    public List<Bean> path;
    public int delayed;
    public int duration;
    public int repeatCount = 1;
    public String time="";
    public int recordScreenWidth;
    public int recordScreenHeight;
    public int recordInsetLeft;
    public int recordInsetTop;
    public int recordInsetRight;
    public int recordInsetBottom;
    public int coordinateVersion;

    public enum  Type {
        Gesture, Delay
    }

    public RecordScriptCmd() {
    }

    public static RecordScriptCmd createGestureCMD(List<Bean> path, int duration,String time) {
        RecordScriptCmd recordScriptCmd = new RecordScriptCmd();
        recordScriptCmd.type = Type.Gesture;
        recordScriptCmd.path = path;
        recordScriptCmd.duration = duration;
        recordScriptCmd.repeatCount = 1;
        recordScriptCmd.time=time;
        return recordScriptCmd;
    }

    public void setRecordMetrics(
            int screenWidth,
            int screenHeight,
            int insetLeft,
            int insetTop,
            int insetRight,
            int insetBottom
    ) {
        this.recordScreenWidth = screenWidth;
        this.recordScreenHeight = screenHeight;
        this.recordInsetLeft = insetLeft;
        this.recordInsetTop = insetTop;
        this.recordInsetRight = insetRight;
        this.recordInsetBottom = insetBottom;
        this.coordinateVersion = 1;
    }

    public static RecordScriptCmd createDelayCMD(int Delay,String time){
        RecordScriptCmd recordScriptCmd = new RecordScriptCmd();
        recordScriptCmd.type=Type.Delay;
        recordScriptCmd.delayed=Delay;
        recordScriptCmd.time=time;
        return recordScriptCmd;
    }

    @Override
    public String toString() {
        return "RecordScriptCmd{" +
                "type=" + type +
                ", path=" + path +
                ", delayed=" + delayed +
                ", duration=" + duration +
                ", repeatCount=" + repeatCount +
                '}';
    }
}
