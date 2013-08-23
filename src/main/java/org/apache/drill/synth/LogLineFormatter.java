package org.apache.drill.synth;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.lang.StringUtils;

/**
 * Log lines can be formated in different ways
 */
public abstract class LogLineFormatter {
    private PrintWriter log;

    public LogLineFormatter(Writer log) {
        this.log = new PrintWriter(log);
    }

    public static LogLineFormatter create(BufferedWriter log, Main.Format format) {
        switch (format) {
            case JSON:
                return new JsonFormatter(log);
            case NCSA:
            	return new NcsaFormatter(log);
            case LOG:
            case CSV:
                return new CsvFormatter(log);
        }
        // can't happen
        return null;
    }

    public abstract void write(LogLine sample) throws IOException;

    public PrintWriter getLog() {
        return log;
    }

    private static class CsvFormatter extends LogLineFormatter {
        public CsvFormatter(BufferedWriter log) {
            super(log);
        }

        public void write(LogLine sample) throws IOException {
            getLog().printf("{%.3f, %08x, %s, ", sample.getT(), sample.getCookie(), sample.getIp().getHostAddress());
            String sep = "\"";
            for (String term : sample.getQuery()) {
                getLog().format("%s%s", sep, term);
                sep = " ";
            }
            getLog().format("\"}\n");
        }
    }
    
    private static class NcsaFormatter extends LogLineFormatter {
    	public NcsaFormatter(BufferedWriter log) {
    		super(log);
    	}
    	
    	public void write(LogLine sample) throws IOException {
    		String query = StringUtils.join(sample.getQuery().toArray(),"+");
    		String stamp = "00/00/0000:00:00:00";
//    		[10/Oct/2000:13:55:36 -0700] 
			Long tl = (long) (sample.getT() * 1000);
			Date time = new Date(tl);
			stamp = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss").format(time);

    		getLog().printf("%s %08x %s [%s] \"GET /?q=%s HTTP/1.0\" 200 0\n", sample.getIp().getHostAddress(), sample.getCookie(), sample.getId(), stamp, query);
    	}
    }

    private static class JsonFormatter extends LogLineFormatter {
        public JsonFormatter(BufferedWriter log) {
            super(log);
        }

        public void write(LogLine sample) throws IOException {
            getLog().printf("{t: %.3f, cookie:\"%08x\", ip:\"%s\", query:", sample.getT(), sample.getCookie(), sample.getIp().getHostAddress());
            String sep = "[";
            for (String term : sample.getQuery()) {
                getLog().format("%s\"%s\"", sep, term);
                sep = ", ";
            }
            getLog().format("]}\n");
        }
    }
}
