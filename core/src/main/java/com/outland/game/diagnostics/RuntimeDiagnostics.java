package com.outland.game.diagnostics;

import com.badlogic.gdx.Gdx;
import java.io.*;
import java.util.Date;

/** Best-effort app-private crash/runtime journal; Android exporter can flush this directory. */
public final class RuntimeDiagnostics {
    private RuntimeDiagnostics() {}
    public static void record(String stage,String message,Throwable error) {
        try {
            if(Gdx.files==null)return;
            File directory=Gdx.files.local("outlandlogs").file();
            if(!directory.exists()&&!directory.mkdirs())return;
            File file=new File(directory,"runtime.log");
            try(FileOutputStream stream=new FileOutputStream(file,true);PrintWriter writer=new PrintWriter(new OutputStreamWriter(stream,"UTF-8"))) {
                writer.println("["+new Date()+"] stage="+stage+" message="+message);
                if(error!=null)error.printStackTrace(writer);
                writer.println();writer.flush();stream.getFD().sync();
            }
        } catch(Throwable ignored) {
            // Diagnostics must never become a second failure source.
        }
    }
}
