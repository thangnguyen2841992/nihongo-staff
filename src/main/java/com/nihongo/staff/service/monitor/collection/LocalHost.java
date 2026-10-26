package com.nihongo.staff.service.monitor.collection;

import java.net.InetAddress;
import java.util.Locale;

/** Name of the machine running this staff-service, and hostname comparison that ignores case and domain suffix. */
public final class LocalHost {
    private LocalHost() {}

    /** Configured name, else COMPUTERNAME (Windows) / HOSTNAME, else the OS host name; lower case without domain. */
    public static String name(String configured) {
        for (String candidate : new String[] {configured, System.getenv("COMPUTERNAME"), System.getenv("HOSTNAME")}) {
            if (candidate != null && !candidate.isBlank()) return shortName(candidate);
        }
        try {
            return shortName(InetAddress.getLocalHost().getHostName());
        } catch (Exception e) {
            return null;
        }
    }

    /** True when either name is unknown, or both name the same machine ("ADMIN-PC" = "admin-pc.lan"). */
    public static boolean same(String registered, String reported) {
        if (registered == null || registered.isBlank() || reported == null || reported.isBlank()) return true;
        return shortName(registered).equals(shortName(reported));
    }

    /** 127.x.x.x, localhost or ::1: the address means "the machine running the collector". */
    public static boolean isLoopback(String host) {
        if (host == null) return false;
        String value = host.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("127.") || value.equals("localhost") || value.equals("::1") || value.equals("[::1]")
                || value.equals("0:0:0:0:0:0:0:1");
    }

    private static String shortName(String name) {
        String value = normalize(name);
        int dot = value.indexOf('.');
        return dot > 0 ? value.substring(0, dot) : value;
    }

    private static String normalize(String name) {
        String value = name.trim().toLowerCase(Locale.ROOT);
        return value.isEmpty() ? null : value;
    }
}
