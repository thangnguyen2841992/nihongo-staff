package com.nihongo.staff.config;

import org.springframework.stereotype.Component;

@Component
public class NodeExporterClient {
    private static final int CONNECT_TIMEOUT = 5000;

    private static final int READ_TIMEOUT = 10000;


    /**
     * Kết nối trực tiếp Node Exporter
     *
     * GET:
     *
     * http://IP:PORT/metrics
     */
    public NodeExporterInfo getInfo(
            String ip,
            Integer port
    ) throws Exception {

        String endpoint =
                "http://"
                        + ip
                        + ":"
                        + port
                        + "/metrics";


        URL url =
                URI.create(endpoint)
                        .toURL();


        HttpURLConnection connection =
                (HttpURLConnection)
                        url.openConnection();


        connection.setRequestMethod(
                "GET"
        );

        connection.setConnectTimeout(
                CONNECT_TIMEOUT
        );

        connection.setReadTimeout(
                READ_TIMEOUT
        );

        connection.setRequestProperty(
                "Accept",
                "text/plain"
        );


        int status =
                connection.getResponseCode();


        if (status != 200) {

            throw new RuntimeException(
                    "Node Exporter trả về HTTP "
                            + status
            );
        }


        NodeExporterInfo info =
                new NodeExporterInfo();


        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        connection.getInputStream(),
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {

            String line;

            while (
                    (line = reader.readLine())
                            != null
            ) {

                parseNodeUnameInfo(
                        line,
                        info
                );
            }
        }


        connection.disconnect();


        /*
         * Node Exporter phải có node_uname_info.
         */

        if (
                info.getHostname() == null
                        && info.getOsType() == null
                        && info.getArchitecture() == null
        ) {

            throw new RuntimeException(
                    "Không tìm thấy metric node_uname_info"
            );
        }


        return info;
    }


    /**
     * Parse:
     *
     * node_uname_info{
     *   machine="x86_64",
     *   nodename="server01",
     *   release="5.15.0",
     *   sysname="Linux"
     * } 1
     */
    private void parseNodeUnameInfo(
            String line,
            NodeExporterInfo info
    ) {

        if (
                line == null
                        || !line.startsWith(
                        "node_uname_info{"
                )
        ) {

            return;
        }


        info.setHostname(
                extractLabel(
                        line,
                        "nodename"
                )
        );


        info.setOsType(
                extractLabel(
                        line,
                        "sysname"
                )
        );


        info.setOsVersion(
                extractLabel(
                        line,
                        "release"
                )
        );


        info.setArchitecture(
                extractLabel(
                        line,
                        "machine"
                )
        );
    }


    /**
     * Lấy:
     *
     * machine="x86_64"
     *
     * -> x86_64
     */
    private String extractLabel(
            String line,
            String label
    ) {

        String search =
                label + "=\"";


        int start =
                line.indexOf(search);


        if (start < 0) {
            return null;
        }


        start +=
                search.length();


        int end =
                line.indexOf(
                        "\"",
                        start
                );


        if (end < 0) {
            return null;
        }


        return line.substring(
                start,
                end
        );
    }
}
