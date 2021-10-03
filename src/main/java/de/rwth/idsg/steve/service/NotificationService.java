/*
 * SteVe - SteckdosenVerwaltung - https://github.com/RWTH-i5-IDSG/steve
 * Copyright (C) 2013-2022 RWTH Aachen University - Information Systems - Intelligent Distributed Systems Group (IDSG).
 * All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package de.rwth.idsg.steve.service;

import com.google.common.base.Strings;
import de.rwth.idsg.steve.NotificationFeature;
import de.rwth.idsg.steve.repository.dto.InsertTransactionParams;
import de.rwth.idsg.steve.repository.dto.UpdateTransactionParams;
import de.rwth.idsg.steve.repository.dto.MailSettings;
import de.rwth.idsg.steve.repository.dto.WebhookSettings;
import lombok.extern.slf4j.Slf4j;
import ocpp.cs._2015._10.RegistrationStatus;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Timer;

import static de.rwth.idsg.steve.NotificationFeature.OcppStationBooted;
import static de.rwth.idsg.steve.NotificationFeature.OcppStationStatusFailure;
import static de.rwth.idsg.steve.NotificationFeature.OcppStationWebSocketConnected;
import static de.rwth.idsg.steve.NotificationFeature.OcppStationWebSocketDisconnected;
import static de.rwth.idsg.steve.NotificationFeature.OcppTransactionStarted;
import static de.rwth.idsg.steve.NotificationFeature.OcppTransactionEnded;
import static java.lang.String.format;

import ocpp.cs._2015._10.MeterValue;

import java.util.ArrayList;
import java.util.List;
//import ocpp.cs._2015._10.SampledValue;

// Added by AR on 23/03/2021
import org.json.JSONObject;

/**
 * @author Sevket Goekay <sevketgokay@gmail.com>
 * @since 22.01.2016
 */
@Slf4j
@Service
public class NotificationService {

    @Autowired private MailService mailService;
    @Autowired private WebhookService webhookService;
    String registration_status_member;

    public void ocppStationBooted(String chargeBoxId, Optional<RegistrationStatus> status) {
        // if (isDisabled_email(OcppStationBooted)) {
        //     return;
        // }

        // String subject = format("Received boot notification from '%s'", chargeBoxId);
        // String body;
        // String registration_status = "";
        // if (status.isPresent()) {
        //     registration_status = status.get().value();
        //     body = format("Charging station '%s' is in database and has registration status '%s'.", chargeBoxId, registration_status);
        // } else {
        //     body = format("Charging station '%s' is NOT in database", chargeBoxId);
        // }

        // System.out.print(body);
        // registration_status_member = registration_status;

        JSONObject bodyObject = new JSONObject();
        // bodyObject.put("boot", 1);
        // bodyObject.put("error", "No error");
        // bodyObject.put("connection", "True");
        // bodyObject.put("connectorId", 0);
        // bodyObject.put("chargeBoxId", chargeBoxId);
        // bodyObject.put("registration", registration_status);
        bodyObject.put("charger", chargeBoxId);
        bodyObject.put("payload", "boot");
        bodyObject.put("version", "2.0.1");
        bodyObject.put("iat", DateTime.now().toString());
        webhookService.sendAsync("charger", bodyObject);

        // mailService.sendAsync(subject, addTimestamp(body));
    }

    public void ocppHeartbeat(String chargeBoxId, String timestamp) {
        JSONObject bodyObject = new JSONObject();
        // bodyObject.put("heartbeat", 1);
        // bodyObject.put("connection", "True");
        // bodyObject.put("connectorId", 0);
        // bodyObject.put("chargeBoxId", chargeBoxId);
        // bodyObject.put("timestamp", timestamp);
        bodyObject.put("charger", chargeBoxId);
        bodyObject.put("iat", timestamp);
        bodyObject.put("version", "2.0.1");
        bodyObject.put("payload", "heartbeat");
        webhookService.sendAsync("charger", bodyObject);
    }

    public void ocppDiagnostics(String chargeBoxId, String status) {
        // JSONObject bodyObject = new JSONObject();
        // bodyObject.put("diagnostics", 1);
        // bodyObject.put("connection","True");
        // bodyObject.put("connectorId","0");
        // bodyObject.put("chargeBoxId",chargeBoxId);
        // bodyObject.put("status",status);
        // bodyObject.put("iat",DateTime.now().toString());
        // webhookService.sendAsync("station", bodyObject);
    }

    public void ocppMetering(String chargeBoxId, int connectorId, List<MeterValue> values) //List<MeterValue> meterValue)   , int transactionId
    {   
        JSONObject bodyObject = new JSONObject();
        bodyObject.put("version", "2.0.1");
        bodyObject.put("payload", "meter");
        bodyObject.put("connector",connectorId);
        bodyObject.put("charger",chargeBoxId);
        bodyObject.put("iat",DateTime.now().toString());
        for (int i = 0; i < values.get(0).getSampledValue().size(); ++i) {
            if (values.get(0).getSampledValue().get(i).getMeasurand().toString().equals("ENERGY_ACTIVE_IMPORT_REGISTER")) {
                bodyObject.put("meter", values.get(0).getSampledValue().get(i).getValue().toString());
                break;
            }
        }
        webhookService.sendAsync("transaction", bodyObject);
    }

    public void ocppStationWebSocketConnected(String chargeBoxId) {
        // if (isDisabled_email(OcppStationWebSocketConnected)) {
        //     return;
        // }

        // String subject = format("Connected to JSON charging station '%s'", chargeBoxId);

        JSONObject bodyObject = new JSONObject();
        // bodyObject.put("socketConnect", 1);
        // bodyObject.put("error", "No error");
        // bodyObject.put("registration", registration_status_member);
        // bodyObject.put("connection", "True");
        // bodyObject.put("connectorId", 0);
        // bodyObject.put("chargeBoxId", chargeBoxId);
        bodyObject.put("version", "2.0.1");
        bodyObject.put("charger", chargeBoxId);
        bodyObject.put("payload", "connect");
        bodyObject.put("iat", DateTime.now().toString());
        webhookService.sendAsync("charger", bodyObject);
        
        // mailService.sendAsync(subject, addTimestamp(""));
    }

    public void ocppStationWebSocketDisconnected(String chargeBoxId) {
        // if (isDisabled_email(OcppStationWebSocketDisconnected)) {
        //     return;
        // }

        // String subject = format("Disconnected from JSON charging station '%s'", chargeBoxId);

        JSONObject bodyObject = new JSONObject();
        // bodyObject.put("socketDisconnect", 1);
        // bodyObject.put("error", "No error");
        // bodyObject.put("registration", registration_status_member);
        // bodyObject.put("connectorId", 0);
        // bodyObject.put("chargeBoxId", chargeBoxId);
        // bodyObject.put("connection", "False");
        bodyObject.put("version", "2.0.1");
        bodyObject.put("charger", chargeBoxId);
        bodyObject.put("payload", "disconnect");
        bodyObject.put("iat", DateTime.now().toString());
        webhookService.sendAsync("charger", bodyObject);

        // mailService.sendAsync(subject, addTimestamp(""));
    }

    public void ocppStationStatusFailure(String chargeBoxId, int connectorId, String errorCode) {
        // if (isDisabled_email(OcppStationStatusFailure)) {
        //     return;
        // }

        // String subject = format("Connector '%s' of charging station '%s' is FAULTED", connectorId, chargeBoxId);
        // String body = format("Status Error Code: '%s'", errorCode);

        // JSONObject bodyObject = new JSONObject();
        // bodyObject.put("version", "2.0.1");
        // bodyObject.put("statusFailure", 1);
        // bodyObject.put("error", errorCode);
        // bodyObject.put("registration", registration_status_member);
        // bodyObject.put("connectorId", connectorId);
        // bodyObject.put("chargeBoxId", chargeBoxId);
        // bodyObject.put("connection", "False");
        // bodyObject.put("iat", DateTime.now().toString());
        // webhookService.sendAsync("station", bodyObject);

        // mailService.sendAsync(subject, addTimestamp(body));
    }

    public void ocppTransactionStarted(int transactionId, InsertTransactionParams params) {
        // if (isDisabled_email(OcppTransactionStarted)) {
        //     return;
        // }

        // String subject = format("Transaction '%s' has started on charging station '%s' on connector '%s'", transactionId, params.getChargeBoxId(), params.getConnectorId());

        JSONObject bodyObject = new JSONObject();
        bodyObject.put("version", "2.0.1");
        bodyObject.put("payload", "start");
        bodyObject.put("charger", params.getChargeBoxId());
        bodyObject.put("connector", params.getConnectorId());
        bodyObject.put("transaction", transactionId);
        bodyObject.put("tag", params.getIdTag());
        bodyObject.put("meter", params.getStartMeterValue());
        bodyObject.put("iat", params.getStartTimestamp().toString());
        webhookService.sendAsync("transaction", bodyObject);

        // mailService.sendAsync(subject, addTimestamp(createContent(params)));
    }

    public void ocppTransactionEnded(UpdateTransactionParams params) {
       // if (isDisabled_email(OcppTransactionEnded)) {
       //      return;
       //  }

        // String subject = format("Transaction '%s' has ended on charging station '%s'", params.getTransactionId(), params.getChargeBoxId());

        JSONObject bodyObject = new JSONObject();
        bodyObject.put("version", "2.0.1");
        bodyObject.put("payload", "stop");
        bodyObject.put("charger", params.getChargeBoxId());
        bodyObject.put("transaction", params.getTransactionId());
        bodyObject.put("iat", params.getStopTimestamp().toString());
        bodyObject.put("reason", params.getStopReason());
        bodyObject.put("meter", params.getStopMeterValue());
        webhookService.sendAsync("transaction", bodyObject);

        // mailService.sendAsync(subject, addTimestamp(createContent(params)));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------


    private static String createContent(InsertTransactionParams params) {
        StringBuilder sb = new StringBuilder("Details:").append(System.lineSeparator())
            .append("- chargeBoxId: ").append(params.getChargeBoxId()).append(System.lineSeparator())
            .append("- connectorId: ").append(params.getConnectorId()).append(System.lineSeparator())
            .append("- idTag: ").append(params.getIdTag()).append(System.lineSeparator())
            .append("- startTimestamp: ").append(params.getStartTimestamp()).append(System.lineSeparator())
            .append("- startMeterValue: ").append(params.getStartMeterValue());

        if (params.isSetReservationId()) {
            sb.append(System.lineSeparator()).append("- reservationId: ").append(params.getReservationId());
        }

        return sb.toString();
    }

    private static String createContent(UpdateTransactionParams params) {
        return new StringBuilder("Details:").append(System.lineSeparator())
            .append("- chargeBoxId: ").append(params.getChargeBoxId()).append(System.lineSeparator())
            .append("- transactionId: ").append(params.getTransactionId()).append(System.lineSeparator())
            .append("- stopTimestamp: ").append(params.getStopTimestamp()).append(System.lineSeparator())
            .append("- stopMeterValue: ").append(params.getStopMeterValue()).append(System.lineSeparator())
            .append("- stopReason: ").append(params.getStopReason())
            .toString();
    }


    private boolean isDisabled_email(NotificationFeature f) {
        MailSettings settings = mailService.getSettings();

        boolean isEnabled = settings.isEnabled()
                && settings.getEnabledFeatures().contains(f)
                && !settings.getRecipients().isEmpty();

        return !isEnabled;
    }


    private boolean isDisabled_webhook(NotificationFeature f) {
        MailSettings settings = mailService.getSettings();

        boolean isEnabled = settings.isEnabled()
                && settings.getEnabledFeatures().contains(f)
                && !settings.getRecipients().isEmpty();

        return !isEnabled;
    }

    private static String addTimestamp(String body) {
        String eventTs = "Timestamp of the event: " + DateTime.now();
        String newLine = System.lineSeparator() + System.lineSeparator();

        if (Strings.isNullOrEmpty(body)) {
            return eventTs;
        } else {
            return body + newLine + "--" + newLine + eventTs;
        }
    }

}
