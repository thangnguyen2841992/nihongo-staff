package com.nihongo.staff.service.monitor.vps;

import com.nihongo.staff.model.monitoring.MonitorVps;
import com.nihongo.staff.model.monitoring.VpsStatus;
import com.nihongo.staff.model.monitoring.dto.MonitorVpsRequest;
import com.nihongo.staff.model.monitoring.dto.MonitorVpsResponse;
import com.nihongo.staff.repository.MonitorVpsRepository;
import com.nihongo.staff.service.monitor.prometheus.PrometheusTargetService;
import com.nihongo.staff.service.monitor.vps.dto.NodeExporterInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MonitorVpsServiceImplI implements IMonitorVpsService {

    private final MonitorVpsRepository repository;

    private final NodeExporterClient nodeExporterClient;

    private final PrometheusTargetService prometheusTargetService;


    /**
     * Đăng ký VPS
     *
     * Flow:
     *
     * 1. Validate IP + port
     * 2. Kết nối trực tiếp Node Exporter
     * 3. Lấy thông tin VPS từ node_uname_info
     * 4. Lưu VPS vào DB
     * 5. Thêm VPS vào Prometheus file_sd
     */
    @Override
    public MonitorVpsResponse register(
            MonitorVpsRequest request
    ) {

        if (request == null) {
            throw new RuntimeException(
                    "Thông tin VPS không được để trống"
            );
        }

        if (request.getIpAddress() == null
                || request.getIpAddress().trim().isEmpty()) {

            throw new RuntimeException(
                    "IP VPS không được để trống"
            );
        }

        String ip =
                request.getIpAddress().trim();

        Integer port =
                request.getAgentPort() == null
                        ? 9100
                        : request.getAgentPort();


        if (port < 1 || port > 65535) {

            throw new RuntimeException(
                    "Port không hợp lệ"
            );
        }


        /*
         * ==========================================
         * 1. CHECK DUPLICATE
         * ==========================================
         */

        if (repository.existsByIpAddressAndAgentPort(
                ip,
                port
        )) {

            throw new RuntimeException(
                    "VPS đã được đăng ký: "
                            + ip
                            + ":"
                            + port
            );
        }


        /*
         * ==========================================
         * 2. CONNECT NODE EXPORTER
         * ==========================================
         *
         * Không query Prometheus ở bước này.
         *
         * Kết nối trực tiếp:
         *
         * http://IP:PORT/metrics
         */

        NodeExporterInfo exporterInfo;

        try {

            exporterInfo =
                    nodeExporterClient.getInfo(
                            ip,
                            port
                    );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Không thể kết nối Node Exporter tại "
                            + ip
                            + ":"
                            + port
                            + ". "
                            + "Hãy kiểm tra Node Exporter và firewall.",
                    e
            );
        }


        /*
         * ==========================================
         * 3. CREATE VPS
         * ==========================================
         */

        MonitorVps vps =
                new MonitorVps();

        vps.setHostname(
                exporterInfo.getHostname() != null
                        && !exporterInfo.getHostname().isBlank()
                        ? exporterInfo.getHostname()
                        : ip
        );

        vps.setIpAddress(ip);

        vps.setAgentPort(port);

        vps.setOsType(
                exporterInfo.getOsType()
        );

        vps.setOsVersion(
                exporterInfo.getOsVersion()
        );

        vps.setArchitecture(
                exporterInfo.getArchitecture()
        );

        vps.setStatus(
                VpsStatus.ONLINE
        );

        vps.setLastSeenAt(
                LocalDateTime.now()
        );


        /*
         * ==========================================
         * 4. SAVE DATABASE
         * ==========================================
         */

        MonitorVps saved =
                repository.save(vps);


        /*
         * ==========================================
         * 5. ADD PROMETHEUS TARGET
         * ==========================================
         */

        try {

            prometheusTargetService.addTarget(
                    saved
            );

        } catch (Exception e) {

            /*
             * VPS đã tồn tại trong DB nhưng
             * Prometheus chưa được cập nhật.
             *
             * Không rollback DB nếu bạn muốn
             * retry Prometheus sau.
             */

            throw new RuntimeException(
                    "Đăng ký VPS thành công nhưng "
                            + "không thể thêm VPS vào Prometheus: "
                            + ip
                            + ":"
                            + port,
                    e
            );
        }


        /*
         * ==========================================
         * 6. RESPONSE
         * ==========================================
         */

        return map(saved);
    }


    /**
     * Lấy danh sách VPS
     */
    @Override
    @Transactional(readOnly = true)
    public List<MonitorVpsResponse> findAll() {

        return repository
                .findAll()
                .stream()
                .map(this::map)
                .toList();
    }


    /**
     * Mapping Entity -> Response
     */
    private MonitorVpsResponse map(
            MonitorVps vps
    ) {

        return MonitorVpsResponse.builder()

                .vpsId(
                        vps.getVpsId()
                )

                .hostname(
                        vps.getHostname()
                )

                .ipAddress(
                        vps.getIpAddress()
                )

                .agentPort(
                        vps.getAgentPort()
                )

                .osType(
                        vps.getOsType()
                )

                .osVersion(
                        vps.getOsVersion()
                )

                .architecture(
                        vps.getArchitecture()
                )

                .status(
                        vps.getStatus()
                )

                .lastSeenAt(
                        vps.getLastSeenAt()
                )

                .build();
    }
}