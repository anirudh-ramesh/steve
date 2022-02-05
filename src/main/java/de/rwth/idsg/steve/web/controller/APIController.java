package de.rwth.idsg.steve.web.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.rwth.idsg.steve.SteveException;
import de.rwth.idsg.steve.ocpp.CommunicationTask;
import de.rwth.idsg.steve.ocpp.OcppTransport;
import de.rwth.idsg.steve.ocpp.RequestResult;
import de.rwth.idsg.steve.repository.*;
import de.rwth.idsg.steve.repository.dto.*;
import de.rwth.idsg.steve.service.ChargePointHelperService;
import de.rwth.idsg.steve.service.ChargePointService16_Client;
import de.rwth.idsg.steve.service.TransactionStopService;
import de.rwth.idsg.steve.utils.ConnectorStatusFilter;
import de.rwth.idsg.steve.web.dto.OcppTagForm;
import de.rwth.idsg.steve.web.dto.OcppTagQueryForm;
import de.rwth.idsg.steve.web.dto.TransactionQueryForm;
import de.rwth.idsg.steve.web.dto.UserQueryForm;
import de.rwth.idsg.steve.web.dto.ocpp.RemoteStartTransactionParams;
import de.rwth.idsg.steve.web.dto.ocpp.RemoteStopTransactionParams;
import jooq.steve.db.tables.records.AddressRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MissingServletRequestParameterException;

import javax.annotation.PostConstruct;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jetty.util.ajax.JSON;
import org.json.JSONObject;
import org.json.JSONArray;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import de.rwth.idsg.steve.web.configuration.SwaggerConfig;

// TODO: Handle DataAccessException when MySQL is down

/**
 * @author Anirudh Ramesh <anirudh@irasus.com>
 * @since 07.03.2021
 */
@Slf4j
@RestController
@CrossOrigin
@RequestMapping(value = "/v1.0.1",produces = MediaType.APPLICATION_JSON_VALUE)
@Api(value = "/v1.0.1", tags = "API", description = "REST API for Irasus EVCMS")
public class APIController {
    private final String sCHARGEBOXID = "/{chargeBoxId}";
    @Autowired
    protected ChargePointHelperService chargePointHelperService;
    @Autowired
    private ChargePointRepository chargePointRepository;
    private ObjectMapper objectMapper;
    @Autowired
    @Qualifier("ChargePointService16_Client")
    private ChargePointService16_Client client16;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private TaskStore taskStore;
    @Autowired
    private TransactionStopService transactionStopService;
    @Autowired
    private OcppTagRepository ocppTagRepository;
    @Autowired
    private UserRepository userRepository;

    @PostConstruct
    private void init() {
        objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @ResponseBody
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Object missingParamterHandler(Exception exception) {
    return new HashMap() {{put("result", "failed"); put("reason", "Required parameter missing");}};
    }

// TODO: Replace getTokenList

    @ApiOperation(httpMethod = "GET", value = "Start a charging transaction", notes = "", tags = {"transaction", "start", "2.0.0-rc2"})
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK"),
        @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 412, message = "Precondition Failed"),
        @ApiResponse(code = 500, message = "Internal Error")
    })
    @GetMapping(value = "/transaction/start")
    public void start_transaction(@RequestParam(name = "charger") String _charger, @RequestParam(name = "tag", defaultValue = "root") String _tag, @RequestParam(name = "connector", defaultValue = "1") String _connector, HttpServletResponse response) throws IOException {
        JSONObject response_object = new JSONObject();
        response_object.put("version", "2.0.0-rc2");
        try {
            if (!getTokenList(_tag).isEmpty()) {
                RemoteStartTransactionParams params = new RemoteStartTransactionParams();
                params.setIdTag(_tag);
                params.setConnectorId(Integer.parseInt(_connector));
                List<ChargePointSelect> cp = new ArrayList<>();
                ChargePointSelect cps = new ChargePointSelect(OcppTransport.JSON, _charger);
                cp.add(cps);
                params.setChargePointSelectList(cp);
                CommunicationTask task = taskStore.get(client16.remoteStartTransaction(params));
                while (!task.isFinished() || task.getResultMap().size() > 1) {}
                RequestResult result = (RequestResult) task.getResultMap().get(_charger);
                if (result.getResponse() == null) {
                    response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
                    response_object.put("charger", _charger);
                    response_object.put("connector", _connector);
                    response_object.put("tag", _tag);
                    response_object.put("response", "Charger disconnected from the EVCMS");
                    writeOutput(response, response_object.toString());
                } else if (!result.getResponse().equals("Accepted")) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response_object.put("charger", _charger);
                    response_object.put("connector", _connector);
                    response_object.put("tag", _tag);
                    response_object.put("response", objectMapper.writeValueAsString(result.getResponse()).replace("\"", ""));
                    writeOutput(response, response_object.toString());
                } else {
                    response.setStatus(HttpServletResponse.SC_OK);
                    response_object.put("charger", _charger);
                    response_object.put("connector", _connector);
                    response_object.put("tag", _tag);
                    response_object.put("response", objectMapper.writeValueAsString(result.getResponse()).replace("\"", ""));
                    writeOutput(response, response_object.toString());
                }
            }
        } catch (NullPointerException nullPointerException) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response_object.put("charger", _charger);
            response_object.put("connector", _connector);
            response_object.put("tag", _tag);
            response_object.put("response", "Request invalid");
            writeOutput(response, response_object.toString());
        } catch (NumberFormatException numberFormatException) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response_object.put("charger", _charger);
            response_object.put("connector", _connector);
            response_object.put("tag", _tag);
            response_object.put("response", "Request invalid");
            writeOutput(response, response_object.toString());
        }
    }

    @ApiOperation(httpMethod = "GET", value = "Stop a charging transaction", notes = "", tags = {"transaction", "stop", "2.0.0-rc2"})
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK"),
        @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 409, message = "Conflict"),
        @ApiResponse(code = 500, message = "Internal Error")
    })
    @GetMapping(value = "/transaction/stop")
    public void stop_transaction(@RequestParam(name = "transaction") String _transaction, HttpServletResponse response) throws IOException {
        JSONObject response_object = new JSONObject();
        response_object.put("version", "2.0.0-rc2");
        try {
            Transaction transaction = transactionRepository.getDetails(Integer.parseInt(_transaction)).getTransaction();
            String _charger = transaction.getChargeBoxId();
            Integer _connector = transaction.getConnectorId();
            RemoteStopTransactionParams params = new RemoteStopTransactionParams();
            List<Integer> transactions = transactionRepository.getActiveTransactionIds(_charger);
            if (transactions.size() > 0) {
                if (transactions.get(transactions.size() - 1) == Integer.parseInt(_transaction)) {
                        params.setTransactionId(Integer.parseInt(_transaction));
                        List<ChargePointSelect> cp = new ArrayList<>();
                        ChargePointSelect cps = new ChargePointSelect(OcppTransport.JSON, _charger);
                        cp.add(cps);
                        params.setChargePointSelectList(cp);
                        CommunicationTask task = taskStore.get(client16.remoteStopTransaction(params));
                        while (!task.isFinished() || task.getResultMap().size() > 1) {}
                        RequestResult result = (RequestResult) task.getResultMap().get(_charger);
                        transactionStopService.stop(transactions);
                        response.setStatus(HttpServletResponse.SC_OK);
                        response_object.put("charger", _charger);
                        response_object.put("connector", _connector.toString());
                        response_object.put("transaction", _transaction);
                        response_object.put("response", objectMapper.writeValueAsString(result.getResponse()).replace("\"", ""));
                        writeOutput(response, response_object.toString());
                    } else {
                        response.setStatus(HttpServletResponse.SC_CONFLICT);
                        response_object.put("charger", _charger);
                        response_object.put("connector", _connector.toString());
                        response_object.put("transaction", _transaction);
                        response_object.put("response", "Transaction properties mismatched");
                        writeOutput(response, response_object.toString());
                }
            } else {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response_object.put("charger", _charger);
                response_object.put("connector", _connector.toString());
                response_object.put("transaction", _transaction);
                response_object.put("response", "Transactions inactive");
                writeOutput(response, response_object.toString());
            }
        } catch (NullPointerException nullPointerException) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response_object.put("charger", "");
            response_object.put("connector", "");
            response_object.put("transaction", _transaction);
            response_object.put("response", "Request invalid");
            writeOutput(response, response_object.toString());
        } catch (NumberFormatException numberFormatException) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response_object.put("charger", "");
            response_object.put("connector", "");
            response_object.put("transaction", _transaction);
            response_object.put("response", "Request invalid");
            writeOutput(response, response_object.toString());
        }
    }

    @ApiOperation(httpMethod = "GET", value = "View the properties of a charging transaction", notes = "", tags = {"transaction", "properties", "2.0.0-rc2"})
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK"),
        @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 500, message = "Internal Error")
    })
    @GetMapping(value = "/transaction")
    public void get_transaction(@ApiParam(required = true, value = "Serial Number of the transaction; '*' for all, '~' for active") @RequestParam(name = "transaction") String _transaction, @ApiParam(allowableValues = "array, object", defaultValue = "object", required = false, value = "Format of the response when returning multiple entries—JSON Array/Object") @RequestParam(name = "response", defaultValue = "object") String _response, HttpServletResponse response) throws IOException {
        JSONArray response_array = new JSONArray();
        JSONObject response_object = new JSONObject();
        response_object.put("version", "2.0.0-rc2");
        if (_transaction.equals("*")) {
            TransactionQueryForm params = new TransactionQueryForm();
            params.setType(TransactionQueryForm.QueryType.ALL);
            if (_response.equals("array")) {
                transactionRepository.getTransactions(params).stream().parallel().forEach(transaction -> {
                    response_array.put(String.valueOf(transaction.getId()));
                });
            } else if (_response.equals("object")) {
                transactionRepository.getTransactions(params).stream().sorted(Comparator.comparingInt(Transaction::getId)).parallel().forEach(transaction -> {
                    JSONObject transactions_object = new JSONObject();
                    transactions_object.put("version", "2.0.0-rc2");
                    transactions_object.put("transaction", String.valueOf(transaction.getId()));
                    transactions_object.put("charger", transaction.getChargeBoxId());
                    transactions_object.put("tag", transaction.getOcppIdTag());
                    transactions_object.put("connector", Integer.toString(transaction.getConnectorId()));
                    transactions_object.put("iat_start", transaction.getStartTimestampDT().toString());
                    transactions_object.put("energy_start", transaction.getStartValue());
                    transactions_object.put("iat_last", transaction.getStartTimestampDT().toString());
                    transactions_object.put("energy_last", transaction.getStartValue());
                    transactions_object.put("consumption_last", "0");
                    try { transactions_object.put("iat_stop", transaction.getStopTimestampDT().toString()); } catch (NullPointerException nullPointerException) {}
                    try { transactions_object.put("reason_stop", transaction.getStopReason()); } catch (NullPointerException nullPointerException) {}
                    try { transactions_object.put("actor_stop", transaction.getStopEventActor().toString()); } catch (NullPointerException nullPointerException) {}
                    try {
                        transactions_object.put("energy_stop", transaction.getStopValue());
                        transactions_object.put("consumption_stop", Integer.toString(Integer.parseInt(transaction.getStopValue()) - Integer.parseInt(transaction.getStartValue())));
                    } catch (NullPointerException nullPointerException) {}
                    transactionRepository.getDetails(transaction
                        .getId())
                        .getValues()
                        .stream()
                        .parallel()
                        .filter(meterValues -> meterValues.getMeasurand().equals("Energy.Active.Import.Register"))
                        // .findFirst()
                        .reduce((first, last) -> last)
                        .ifPresent(meterValues -> {
                            transactions_object.put("consumption_last", Integer.toString(Integer.parseInt(meterValues.getValue()) - Integer.parseInt(transaction.getStartValue())));
                            transactions_object.put("energy_last", meterValues.getValue());
                            transactions_object.put("iat_last", meterValues.getValueTimestamp().toString());
                        });
                        // .ifPresentOrElse((meterValues -> {
                            // transactions_object.put("energy_last", meterValues.getValue());
                            // transactions_object.put("iat_last", meterValues.getValueTimestamp().toString());
                        // }), () -> System.out.println(""));
                    response_array.put(transactions_object);
                });
            }
            response.setStatus(HttpServletResponse.SC_OK);
            writeOutput(response, response_array.toString());
        } else if (_transaction.equals("~")) {
            TransactionQueryForm params = new TransactionQueryForm();
            params.setType(TransactionQueryForm.QueryType.ACTIVE);
            if (_response.equals("array")) {
                transactionRepository.getTransactions(params).stream().parallel().forEach(transaction -> {
                    response_array.put(String.valueOf(transaction.getId()));
                });
            } else if (_response.equals("object")) {
                transactionRepository.getTransactions(params).stream().parallel().forEach(transaction -> {
                    JSONObject transactions_object = new JSONObject();
                    transactions_object.put("version", "2.0.0-rc2");
                    transactions_object.put("transaction", String.valueOf(transaction.getId()));
                    transactions_object.put("charger", transaction.getChargeBoxId());
                    transactions_object.put("tag", transaction.getOcppIdTag());
                    transactions_object.put("connector", Integer.toString(transaction.getConnectorId()));
                    transactions_object.put("iat_start", transaction.getStartTimestampDT().toString());
                    transactions_object.put("energy_start", transaction.getStartValue());
                    transactions_object.put("iat_last", transaction.getStartTimestampDT().toString());
                    transactions_object.put("energy_last", transaction.getStartValue());
                    transactions_object.put("consumption_last", "0");
                    try { transactions_object.put("iat_stop", transaction.getStopTimestampDT().toString()); } catch (NullPointerException nullPointerException) {}
                    try { transactions_object.put("reason_stop", transaction.getStopReason()); } catch (NullPointerException nullPointerException) {}
                    try { transactions_object.put("actor_stop", transaction.getStopEventActor().toString()); } catch (NullPointerException nullPointerException) {}
                    try {
                        transactions_object.put("energy_stop", transaction.getStopValue());
                        transactions_object.put("consumption_stop", Integer.toString(Integer.parseInt(transaction.getStopValue()) - Integer.parseInt(transaction.getStartValue())));
                    } catch (NullPointerException nullPointerException) {}
                    transactionRepository.getDetails(transaction
                        .getId())
                        .getValues()
                        .stream()
                        .parallel()
                        .filter(meterValues -> meterValues.getMeasurand().equals("Energy.Active.Import.Register"))
                        // .findFirst()
                        .reduce((first, last) -> last)
                        .ifPresent(meterValues -> {
                            transactions_object.put("consumption_last", Integer.toString(Integer.parseInt(meterValues.getValue()) - Integer.parseInt(transaction.getStartValue())));
                            transactions_object.put("energy_last", meterValues.getValue());
                            transactions_object.put("iat_last", meterValues.getValueTimestamp().toString());
                        });
                        // .ifPresentOrElse((meterValues -> {
                            // transactions_object.put("energy_last", meterValues.getValue());
                            // transactions_object.put("iat_last", meterValues.getValueTimestamp().toString());
                        // }), () -> System.out.println(""));
                    response_array.put(transactions_object);
                });
            }
            response.setStatus(HttpServletResponse.SC_OK);
            writeOutput(response, response_array.toString());
        } else {
            if (_response.equals("array")) {
                response_object.put("warning", "\"array\" response format is applicable only when returning multiple entries");
            }
            try {
                TransactionDetails transactionDetails = transactionRepository.getDetails(Integer.parseInt(_transaction));
                List<TransactionDetails.MeterValues> intermediateValues = transactionDetails.getValues();
                Transaction transaction = transactionDetails.getTransaction();
                String energy_start = transaction.getStartValue();
                String energy_last = energy_start;
                String iat_last = transaction.getStartTimestampDT().toString();
                Integer consumption_last = 0;
                for (int intermediateValue = (intermediateValues.size() - 1); intermediateValue != -1; --intermediateValue) {
                    if (intermediateValues.get(intermediateValue).getMeasurand().equals("Energy.Active.Import.Register")) {
                        consumption_last = Integer.parseInt(intermediateValues.get(intermediateValue).getValue()) - Integer.parseInt(energy_start);
                        energy_last = intermediateValues.get(intermediateValue).getValue().toString();
                        iat_last = intermediateValues.get(intermediateValue).getValueTimestamp().toString();
                        break;
                    }
                }
                response.setStatus(HttpServletResponse.SC_OK);
                try { response_object.put("iat_stop", transaction.getStopTimestampDT().toString()); } catch (NullPointerException nullPointerException) {}
                try { response_object.put("reason_stop", transaction.getStopReason()); } catch (NullPointerException nullPointerException) {}
                try { response_object.put("actor_stop", transaction.getStopEventActor().toString()); } catch (NullPointerException nullPointerException) {}
                try {
                    response_object.put("energy_stop", transaction.getStopValue());
                    response_object.put("consumption_stop", Integer.toString(Integer.parseInt(transaction.getStopValue()) - Integer.parseInt(energy_start)));
                } catch (NullPointerException nullPointerException) {}
                response_object.put("consumption_last", Integer.toString(consumption_last));
                response_object.put("transaction", _transaction);
                response_object.put("energy_start", energy_start);
                response_object.put("iat_start", transaction.getStartTimestampDT().toString());
                response_object.put("energy_last", energy_last);
                response_object.put("iat_last", iat_last);
                response_object.put("charger", transaction.getChargeBoxId());
                response_object.put("connector", Integer.toString(transaction.getConnectorId()));
                response_object.put("tag", transaction.getOcppIdTag());
                writeOutput(response, response_object.toString());
            } catch (NullPointerException nullPointerException) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                writeOutput(response, "{\"version\":\"2.0.0-rc2\"}");
            } catch (NumberFormatException numberFormatException) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                writeOutput(response, "{\"version\":\"2.0.0-rc2\"}");
            }
        }
    }

// TODO: Add parameter for return type i.e. JSONObject or JSONArray

    @ApiOperation(httpMethod = "GET", value = "View the properties of a charger", notes = "", tags = {"charger", "properties", "2.0.0-rc2"})
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK"),
        @ApiResponse(code = 404, message = "Not Found"),
        @ApiResponse(code = 500, message = "Internal Error")
    })
    @GetMapping(value = "/charger")
    public void get_charger(@ApiParam(required = true, value = "Name of the charger; '*' for all, '~' for active") @RequestParam(name = "charger") String _charger, @ApiParam(allowableValues = "array, object", defaultValue = "object", required = false, value = "Format of the response when returning multiple entries—JSON Array/Object") @RequestParam(name = "response", defaultValue = "object") String _response, HttpServletResponse response) throws IOException {
        JSONArray response_array = new JSONArray();
        JSONObject response_object = new JSONObject();
        response_object.put("version", "2.0.0-rc2");
        if (_charger.equals("*")) {
            if (_response.equals("array")) {
                List<String> chargers = new ArrayList<>();
                chargers = chargePointRepository.getChargeBoxIds().stream().collect(Collectors.toList());
                for (int charger = 0; charger < chargers.size(); ++charger) {
                    response_array.put(chargers.get(charger));
                }
            } else if (_response.equals("object")) {
// TODO
                chargePointRepository.getChargeBoxIds().forEach(charger0 -> {
                    System.out.println(charger0);
                });
                response_array.put(response_object);
            }
            response.setStatus(HttpServletResponse.SC_OK);
            writeOutput(response, response_array.toString());
        } else if (_charger.equals("~")) {
            List<ConnectorStatus> status_connectors = ConnectorStatusFilter.filterAndPreferZero(chargePointRepository.getChargePointConnectorStatus());
            if (_response.equals("array")) {
                chargePointHelperService.getOcppJsonStatus().forEach(charger0 -> {
                    response_array.put(charger0.getChargeBoxId());
                });
            } else if (_response.equals("object")) {
                chargePointHelperService.getOcppJsonStatus().forEach(charger0 -> {
                    // AddressRecord addressRecord = charger0.getAddress();
                    response_object.put("name_charger", charger0.getChargeBoxId());
                    response_object.put("connection_start", charger0.getConnectedSinceDT().toString());
                    // try { response_object.put("serialNumber_charger", charger0.getChargeBoxSerialNumber()); } catch (NullPointerException nullPointerException) {}
                    // try { response_object.put("model_charger", charger0.getChargePointModel()); } catch (NullPointerException nullPointerException) {}
                    // try { response_object.put("manufacturer_charger", charger0.getChargePointVendor()); } catch (NullPointerException nullPointerException) {}
                    // try { response_object.put("serialNumber_meter", charger0.getMeterSerialNumber()); } catch (NullPointerException nullPointerException) {}
                    // try { response_object.put("type_meter", charger0.getMeterType()); } catch (NullPointerException nullPointerException) {}
                    // try { response_object.put("note_charger", charger0.getNote()); } catch (NullPointerException nullPointerException) {}
                    // try { response_object.put("description_charger", charger0.getDescription()); } catch (NullPointerException nullPointerException) {}
                    // try { response_object.put("latitude_location", String.valueOf(charger0.getLocationLatitude())); } catch (NullPointerException nullPointerException) {}
                    // try { response_object.put("longitude_location", String.valueOf(charger0.getLocationLongitude())); } catch (NullPointerException nullPointerException) {}
                    // try { response_object.put("street_location", addressRecord.getStreet()); } catch (NullPointerException nullPointerException) {}
                    // try { response_object.put("number_location", addressRecord.getHouseNumber()); } catch (NullPointerException nullPointerException) {}
                    // try { response_object.put("country_location", addressRecord.getCountry()); } catch (NullPointerException nullPointerException) {}
                    // try { response_object.put("pincode_location", addressRecord.getZipCode()); } catch (NullPointerException nullPointerException) {}
                    // try { response_object.put("city_location", addressRecord.getCity()); } catch (NullPointerException nullPointerException) {}
                    response_object.put("status_connector", status_connectors.stream().parallel().filter(charger1 -> charger0.getChargeBoxId().equals(charger1.getChargeBoxId())).findAny().orElse(null).getStatus());
                    // try { response_object.put("heartbeat_charger", charger0.getLastHeartbeatTimestamp()); } catch (NullPointerException nullPointerException) {}
                    response_array.put(response_object);
                });
            }
            response.setStatus(HttpServletResponse.SC_OK);
            writeOutput(response, response_array.toString());
        } else {
            if (_response.equals("array")) {
                response_object.put("warning", "\"array\" response format is applicable only when returning multiple entries");
            }
            List<String> chargers = new ArrayList<>();
            chargers.add(_charger);
            try {
// TODO: Add support for other keys e.g. connectedSince, connectionDuration, connectedSinceDT
                ChargePoint.Details charger = chargePointRepository.getDetails(chargePointRepository.getChargeBoxIdPkPair(chargers).get(_charger));
                List<ConnectorStatus> status_connectors = ConnectorStatusFilter.filterAndPreferZero(chargePointRepository.getChargePointConnectorStatus());
                AddressRecord addressRecord = charger.getAddress();
                response_object.put("name_charger", charger.getChargeBox().getChargeBoxId());
                try { response_object.put("serialNumber_charger", charger.getChargeBox().getChargeBoxSerialNumber()); } catch (NullPointerException nullPointerException) {}
                try { response_object.put("model_charger", charger.getChargeBox().getChargePointModel()); } catch (NullPointerException nullPointerException) {}
                try { response_object.put("manufacturer_charger", charger.getChargeBox().getChargePointVendor()); } catch (NullPointerException nullPointerException) {}
                try { response_object.put("serialNumber_meter", charger.getChargeBox().getMeterSerialNumber()); } catch (NullPointerException nullPointerException) {}
                try { response_object.put("type_meter", charger.getChargeBox().getMeterType()); } catch (NullPointerException nullPointerException) {}
                try { response_object.put("note_charger", charger.getChargeBox().getNote()); } catch (NullPointerException nullPointerException) {}
                try { response_object.put("description_charger", charger.getChargeBox().getDescription()); } catch (NullPointerException nullPointerException) {}
                try { response_object.put("latitude_location", String.valueOf(charger.getChargeBox().getLocationLatitude())); } catch (NullPointerException nullPointerException) {}
                try { response_object.put("longitude_location", String.valueOf(charger.getChargeBox().getLocationLongitude())); } catch (NullPointerException nullPointerException) {}
                try { response_object.put("ICCID", String.valueOf(charger.getChargeBox().getIccid())); } catch (NullPointerException nullPointerException) {}
                try { response_object.put("IMSI", String.valueOf(charger.getChargeBox().getImsi())); } catch (NullPointerException nullPointerException) {}
                try { response_object.put("timestamp_firmware", String.valueOf(charger.getChargeBox().getFwUpdateTimestamp())); } catch (NullPointerException nullPointerException) {}
                try { response_object.put("version_firmware", String.valueOf(charger.getChargeBox().getFwVersion())); } catch (NullPointerException nullPointerException) {}
                try { response_object.put("status_firmware", String.valueOf(charger.getChargeBox().getFwUpdateStatus())); } catch (NullPointerException nullPointerException) {}
                try { response_object.put("protocol", String.valueOf(charger.getChargeBox().getOcppProtocol())); } catch (NullPointerException nullPointerException) {}
                try { response_object.put("registration", String.valueOf(charger.getChargeBox().getRegistrationStatus())); } catch (NullPointerException nullPointerException) {}
                try { response_object.put("street_location", addressRecord.getStreet()); } catch (NullPointerException nullPointerException) {}
                try { response_object.put("number_location", addressRecord.getHouseNumber()); } catch (NullPointerException nullPointerException) {}
                try { response_object.put("country_location", addressRecord.getCountry()); } catch (NullPointerException nullPointerException) {}
                try { response_object.put("pincode_location", addressRecord.getZipCode()); } catch (NullPointerException nullPointerException) {}
                try { response_object.put("city_location", addressRecord.getCity()); } catch (NullPointerException nullPointerException) {}
                try { response_object.put("status_connector", status_connectors.stream().parallel().filter(cs -> _charger.equals(cs.getChargeBoxId())).findAny().orElse(null).getStatus()); } catch (NullPointerException nullPointerException) {}
                try { response_object.put("heartbeat_charger", charger.getChargeBox().getLastHeartbeatTimestamp()); } catch (NullPointerException nullPointerException) {}
                response.setStatus(HttpServletResponse.SC_OK);
                writeOutput(response, response_object.toString());
            } catch (NullPointerException nullPointerException) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                writeOutput(response, "{\"version\":\"2.0.0-rc2\"}");
            }
        }
    }

    @ApiOperation(httpMethod = "GET", value = "View the properties of a charger's connector(s)", notes = "", tags = {"connector", "properties", "2.0.0-rc2"})
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK"),
        @ApiResponse(code = 500, message = "Internal Error")
    })
    @GetMapping(value = "/connector")
    public void get_connector(@ApiParam(required = true, value = "Name of the charger") @RequestParam(name = "charger") String _charger, HttpServletResponse response) throws IOException {
        List<Integer> connectors = chargePointRepository.getNonZeroConnectorIds(_charger);
        JSONArray response_array = new JSONArray();
        JSONObject response_object = new JSONObject();
        response_object.put("version", "2.0.0-rc2");
        for (int connector = 0; connector < connectors.size(); ++connector) {
            response_array.put(Integer.toString(connectors.get(connector)));
        }
        response_object.put("name_charger", _charger);
        response_object.put("count_connectors", Integer.toString(connectors.size()));
        response_object.put("address_connectors", response_array);
        response.setStatus(HttpServletResponse.SC_OK);
        writeOutput(response, response_object.toString());
    }

// TODO: Add parameter for return type i.e. JSONObject or JSONArray

    @ApiOperation(httpMethod = "GET", value = "View the properties of a tag", notes = "", tags = {"tag", "properties", "2.0.0-rc2"})
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK"),
        @ApiResponse(code = 500, message = "Internal Error")
    })
    @GetMapping(value = "/tag")
    public void get_tag(@ApiParam(required = true, value = "Only '*' supported") @RequestParam(name = "tag") String _tag, @ApiParam(allowableValues = "array, object", defaultValue = "object", required = false, value = "Format of the response when returning multiple entries—JSON Array/Object") @RequestParam(name = "response", defaultValue = "object") String _response, HttpServletResponse response) throws IOException {
        JSONArray response_array = new JSONArray();
        if (_tag.equals("*")) {
            if (_response.equals("array")) {
                ocppTagRepository.getIdTags().stream().forEach(tag -> response_array.put(tag));
            } else if (_response.equals("object")) {
                // List<String> tags = new ArrayList<>();
                // tags = ocppTagRepository.getIdTags().stream().collect(Collectors.toList());
                // for (int tag = 0; tag < tags.size(); ++tag) {
                //     JSONObject tag_object = new JSONObject();
                //     tag_object.put("tag", tags.get(tag));
                //     tag_object.put("version", "2.0.0-rc2");
                //     response_array.put(tag_object);
                // }
                ocppTagRepository.getOverview(new OcppTagQueryForm()).stream().forEach(tag -> System.out.println(tag.getIdTag()));
            }
            response.setStatus(HttpServletResponse.SC_OK);
            writeOutput(response, response_array.toString());
        } else {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            writeOutput(response, "{\"version\":\"2.0.0-rc2\"}");
        }
    }

    @ApiOperation(httpMethod = "POST", value = "Create a new tag", notes = "", tags = {"tag", "properties", "2.0.0-rc2"})
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Created"),
        @ApiResponse(code = 400, message = "Bad Request"),
        @ApiResponse(code = 409, message = "Conflict"),
        @ApiResponse(code = 500, message = "Internal Error")
    })
    @PostMapping(value = "/tag")
    public void post_tag(@RequestBody String _tag, HttpServletResponse response) throws IOException {
        try {
            HashMap<String, String> tag_object = objectMapper.readValue(_tag, HashMap.class);
            String tag_string = tag_object.get("tag");
            if (tag_string == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                writeOutput(response, "{\"version\":\"2.0.0-rc2\"}");
            } else {
                String parent_string = tag_object.get("parent");
                if (parent_string != null) {
                    // Optional<OcppTag.Overview> parent_tag = ocppTagRepository.getOverview(new OcppTagQueryForm()).stream().filter(o -> o.getIdTag().equals(_parent)).findFirst();
                    // if (parent_string.isPresent()) {
                    //     // TODO: Proceed to MaxActiveTransactionCount
                    // } else {
                    //     response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    //     writeOutput(response, "{\"version\":\"2.0.0-rc2\"}");
                    // }
                } else {
                    Integer maxActiveTransactionCount = 1;
                    try {
                        maxActiveTransactionCount = Integer.parseInt(tag_object.get("MaxActiveTransactionCount"));
                        String note = tag_object.get("note");
                        if (note == null) note = "";
                        if (parent_string == null) parent_string = "";
                        JSONObject response_object = new JSONObject();
                        response_object.put("tag", tag_string);
                        response_object.put("MaxActiveTransactionCount", maxActiveTransactionCount.toString());
                        response_object.put("note", note);
                        response_object.put("parent", parent_string);
                        OcppTagForm tag_form = new OcppTagForm();
                        tag_form.setIdTag(tag_string);
                        tag_form.setParentIdTag(parent_string);
                        tag_form.setMaxActiveTransactionCount(maxActiveTransactionCount);
                        tag_form.setNote(note);
                        try {
                            ocppTagRepository.addOcppTag(tag_form);
                            response.setStatus(HttpServletResponse.SC_CREATED);
                            writeOutput(response, response_object.toString());
                        } catch (SteveException steveException) {
                            response.setStatus(HttpServletResponse.SC_CONFLICT);
                            response_object.put("response", "Tag must be unique");
                            writeOutput(response, response_object.toString());
                        } catch (Exception exception) {
                            // exception.printStackTrace();
                            response.setStatus(HttpServletResponse.SC_CONFLICT);
                            writeOutput(response, "{\"version\":\"2.0.0-rc2\"}");
                        }
                    } catch (NumberFormatException numberFormatException) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        writeOutput(response, "{\"version\":\"2.0.0-rc2\"}");
                    }
                }
            }
        } catch (ClassCastException classCastException) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeOutput(response, "{\"version\":\"2.0.0-rc2\"}");
        }
    }

    @ApiOperation(httpMethod = "DELETE", value = "Delete an unblocked, non-parent tag", notes = "", tags = {"tag", "properties", "2.0.0-rc2"})
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK"),
        @ApiResponse(code = 400, message = "Bad Request"),
        @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 500, message = "Internal Error")
    })
    @DeleteMapping(value = "/tag")
    public void delete_tag(@RequestParam("tag") String _tag, HttpServletResponse response) throws IOException {
        Optional<OcppTag.Overview> tag = ocppTagRepository.getOverview(new OcppTagQueryForm()).stream().filter(o -> o.getIdTag().equals(_tag)).findFirst();
        if (tag.isPresent()) {
            if (tag.get().getParentOcppTagPk() != null) {
                ocppTagRepository.deleteOcppTag(tag.get().getOcppTagPk());
                response.setStatus(HttpServletResponse.SC_OK);
                writeOutput(response, "{\"version\":\"2.0.0-rc2\"}");
            } else {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                writeOutput(response, "{\"version\":\"2.0.0-rc2\"}");
            }
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeOutput(response, "{\"version\":\"2.0.0-rc2\"}");
        }
    }

// Code break added by Anirudh

    @ApiOperation(httpMethod = "PUT", value = "Update the properties of a tag", notes = "", tags = {"tag", "properties", "2.0.0-rc2"})
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK"),
        @ApiResponse(code = 400, message = "Bad Request"),
        @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 500, message = "Internal Error")
    })
    @PutMapping(value = "/tag")
    public void put_tag(@RequestParam("tag") String _tag, @RequestParam(name = "note", defaultValue = "") String _note, @RequestParam(name = "parent", defaultValue = "root") String _parent, @RequestParam(name = "MaxActiveTransactionCount", defaultValue = "1") String _MaxActiveTransactionCount, HttpServletResponse response) throws IOException {
        Optional<OcppTag.Overview> tag = ocppTagRepository.getOverview(new OcppTagQueryForm()).stream().filter(o -> o.getIdTag().equals(_tag)).findFirst();
        if (tag.isPresent()) {
            // ocppTagRepository.getRecord(tag.get().getOcppTagPk()).getNote();
            // ocppTagRepository.getRecord(tag.get().getOcppTagPk()).getParentIdTag();
            // ocppTagRepository.getRecord(tag.get().getOcppTagPk()).getMaxActiveTransactionCount();
            Optional<OcppTag.Overview> parent = ocppTagRepository.getOverview(new OcppTagQueryForm()).stream().filter(o -> o.getIdTag().equals(_parent)).findFirst();
            if (parent.isPresent()) {
                OcppTagForm tag_form = new OcppTagForm();
                tag_form.setOcppTagPk(tag.get().getOcppTagPk());
                tag_form.setParentIdTag(_parent);
                tag_form.setMaxActiveTransactionCount(Integer.parseInt(_MaxActiveTransactionCount));
                tag_form.setNote(_note);
                try {
                    ocppTagRepository.updateOcppTag(tag_form);
                    response.setStatus(HttpServletResponse.SC_CREATED);
                    // writeOutput(response, response_object.toString());
                    writeOutput(response, "{\"version\":\"2.0.0-rc2\"}");
                } catch (SteveException steveException) {
                    response.setStatus(HttpServletResponse.SC_CONFLICT);
                    // response_object.put("response", "Tag must be unique");
                    // writeOutput(response, response_object.toString());
                    writeOutput(response, "{\"version\":\"2.0.0-rc2\"}");
                } catch (Exception exception) {
                    // exception.printStackTrace();
                    response.setStatus(HttpServletResponse.SC_CONFLICT);
                    writeOutput(response, "{\"version\":\"2.0.0-rc2\"}");
                }
            } else {
                System.out.println("parent not present");
            }
        } else {
            System.out.println("Tag not present");
        }
        writeOutput(response, "{\"version\":\"2.0.0-rc2\"}");
    }

    @ApiOperation(httpMethod = "GET", value = "Start Transaction", notes = "INPUT: chargeBoxId\n\nRETURN: Start Transaction Parameters", tags = "1.0.1")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 500, message = "Internal Error")
    })
    @GetMapping(value = "/startTransaction/" + sCHARGEBOXID)
    public void startRemoteTransaction(@PathVariable("chargeBoxId") String chargeBoxId,
                                   HttpServletResponse response) throws IOException {
        try {
            if (!getTokenList("root").isEmpty()) {
                RemoteStartTransactionParams params = new RemoteStartTransactionParams();
                params.setIdTag("root");
                // params.setConnectorId(2);
                params.setConnectorId(1);
                List<ChargePointSelect> cp = new ArrayList<>();
                ChargePointSelect cps = new ChargePointSelect(OcppTransport.JSON, chargeBoxId);
                cp.add(cps);
                params.setChargePointSelectList(cp);
                CommunicationTask task = taskStore.get(client16.remoteStartTransaction(params));
                while (!task.isFinished() || task.getResultMap().size() > 1) {}
                RequestResult result = (RequestResult) task.getResultMap().get(chargeBoxId);
                if (result.getResponse() == null) {
                    response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
                    JSONObject response_object = new JSONObject();
                    response_object.put("chargeBoxId", chargeBoxId);
                    // response_object.put("connectorId", 2);
                    response_object.put("connectorId", 1);
                    response_object.put("tag", "root");
                    response_object.put("status", "failed");
                    response_object.put("response", "Charger disconnected from the CMS");
                    writeOutput(response, response_object.toString());
                } else if (!result.getResponse().equals("Accepted")) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    JSONObject response_object = new JSONObject();
                    response_object.put("chargeBoxId", chargeBoxId);
                    // response_object.put("connectorId", 2);
                    response_object.put("connectorId", 1);
                    response_object.put("tag", "root");
                    response_object.put("status", "failed");
                    response_object.put("response", objectMapper.writeValueAsString(result.getResponse()));
                    writeOutput(response, response_object.toString());
                } else {
                    JSONObject response_object = new JSONObject();
                    response_object.put("chargeBoxId", chargeBoxId);
                    // response_object.put("connectorId", 2);
                    response_object.put("connectorId", 1);
                    response_object.put("tag", "root");
                    response_object.put("status", "created");
                    response_object.put("response", "Accepted");
                    writeOutput(response, response_object.toString());
                }
            }
        } catch (NullPointerException nullPointerException) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            writeOutput(response, "{\"chargeBoxId\":\"" + chargeBoxId + "\",\"connectorId\":\"2\",\"tag\":\"root\",\"status\":\"failed\",\"response\":\"Request invalid\"}");
        }
    }

    @ApiOperation(httpMethod = "GET", value = "Start Session", notes = "INPUT: chargeBoxId, Tag Id\n\nRETURN: Started Session's Info.", tags = "1.0.1")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 500, message = "Internal Error")
    })
    @GetMapping(value = sCHARGEBOXID + "/startSession/{idTag}")
    public void startRemoteSession(@PathVariable("chargeBoxId") String chargeBoxId,
                                   @PathVariable("idTag") String idTag,
                                   HttpServletResponse response) throws IOException {
        try {
            if (!getTokenList(idTag).isEmpty()) {
                RemoteStartTransactionParams params = new RemoteStartTransactionParams();
                params.setIdTag(idTag);
                // params.setConnectorId(2);
                params.setConnectorId(1);
                List<ChargePointSelect> cp = new ArrayList<>();
                ChargePointSelect cps = new ChargePointSelect(OcppTransport.JSON, chargeBoxId);
                cp.add(cps);
                params.setChargePointSelectList(cp);
                CommunicationTask task = taskStore.get(client16.remoteStartTransaction(params));
                while (!task.isFinished() || task.getResultMap().size() > 1) {
                    // System.out.println("[startSession] wait for");
                }
                RequestResult result = (RequestResult) task.getResultMap().get(chargeBoxId);
                if (result.getResponse() == null) {
                    response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
                    JSONObject response_object = new JSONObject();
                    response_object.put("chargeBoxId", chargeBoxId);
                    // response_object.put("connectorId", 2);
                    response_object.put("connectorId", 1);
                    response_object.put("tag", idTag);
                    response_object.put("status", "failed");
                    response_object.put("response", "Charger disconnected from the CMS");
                    writeOutput(response, response_object.toString());
                } else if (!result.getResponse().equals("Accepted")) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    JSONObject response_object = new JSONObject();
                    response_object.put("chargeBoxId", chargeBoxId);
                    // response_object.put("connectorId", 2);
                    response_object.put("connectorId", 1);
                    response_object.put("tag", idTag);
                    response_object.put("status", "failed");
                    response_object.put("response", objectMapper.writeValueAsString(result.getResponse()));
                    writeOutput(response, response_object.toString());
                } else {
                    JSONObject response_object = new JSONObject();
                    response_object.put("chargeBoxId", chargeBoxId);
                    // response_object.put("connectorId", 2);
                    response_object.put("connectorId", 1);
                    response_object.put("tag", idTag);
                    response_object.put("status", "created");
                    response_object.put("response", objectMapper.writeValueAsString(result.getResponse()));
                    writeOutput(response, response_object.toString());
                }
            }
        } catch (NullPointerException nullPointerException) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            writeOutput(response, "{\"chargeBoxId\":\"" + chargeBoxId + "\",\"connectorId\":\"2\",\"tag\":\"" + idTag + "\",\"status\":\"failed\",\"response\":\"Request invalid\"}");
        }
    }

    // @ApiOperation(httpMethod = "GET", value = "Get value", notes = "description", tags = "1.0.1")
    // @ApiResponses(value = {
    //         @ApiResponse(code = 200, message = "OK"),
    //         @ApiResponse(code = 500, message = "Internal Error")
    // })
    // @GetMapping(value = sCHARGEBOXID + "/getMeter/{transactionId}")
    // public void getMeter0(@PathVariable("chargeBoxId") String chargeBoxId,
    //                         @PathVariable("transactionId") String transactionId,
    //                                   HttpServletResponse response) throws IOException {
    //     List<Integer> transactionIDs = transactionRepository.getActiveTransactionIds(chargeBoxId);
    //     // TransactionDetails transactionDetails = new TransactionDetails();
    //     if (transactionIDs.size() > 0) {
    //         if (transactionIDs.get(transactionIDs.size() - 1) == Integer.parseInt(transactionId)) {
    //             TransactionDetails transactionDetails = transactionRepository.getDetails(Integer.parseInt(transactionId));
    //             List<TransactionDetails.MeterValues> intermediateValues = transactionDetails.getValues();
    //             System.out.println(intermediateValues.get(intermediateValues.size() - 1).getValue());
    //             writeOutput(response, "");
    //         } else {
    //             writeOutput(response, "");
    //         }
    //     } else {
    //         TransactionDetails transactionDetails = transactionRepository.getDetails(Integer.parseInt(transactionId));
    //         List<TransactionDetails.MeterValues> intermediateValues = transactionDetails.getValues();
    //         System.out.println(intermediateValues.get(intermediateValues.size() - 1).getValue());
    //         writeOutput(response, "");
    //     }
    // }

    @ApiOperation(httpMethod = "GET", value = "Meter Reading", notes = "INPUT: chargeBoxId, Transaction ID\n\nRETURN VALUE: Transaction's Metering Information.", tags = "1.0.1")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 500, message = "Internal Error")
    })
    @GetMapping(value = "/getMeter/{transactionId}")
    public void getMeter(@PathVariable("transactionId") String transactionId,
                                      HttpServletResponse response) throws IOException {
        TransactionDetails thisTxDetails = transactionRepository.getDetails(Integer.parseInt(transactionId));
        List<TransactionDetails.MeterValues> intermediateValues = thisTxDetails.getValues();
        Transaction thisTx = thisTxDetails.getTransaction();
        String energy_last, iat_last;
        String charger = thisTx.getChargeBoxId();
        String tag = thisTx.getOcppIdTag();
        String connector = Integer.toString(thisTx.getConnectorId());
        String energy_start = thisTx.getStartValue();
        String iat_start = thisTx.getStartTimestampDT().toString();
        try {
            energy_last = intermediateValues.get(intermediateValues.size() - 1).getValue();
            iat_last = intermediateValues.get(intermediateValues.size() - 1).getValueTimestamp().toString();
        } catch (IndexOutOfBoundsException e) {
            energy_last = energy_start;
            iat_last = iat_start;
        }

        JSONObject response_object = new JSONObject();
        response_object.put("transactionId", transactionId);
        response_object.put("chargeBoxId", charger);
        response_object.put("tag", tag);
        response_object.put("connectorId", connector);
        response_object.put("startValue", energy_start);
        response_object.put("startValueTimestamp", iat_start);
        response_object.put("lastValue", energy_last);
        response_object.put("lastValueTimestamp", iat_last);
        writeOutput(response, response_object.toString());
        //writeOutput(response, "{\"transactionId\":\"" + transactionId + "\",\"chargeBoxId\":\"" + charger + "\",\"tag\":\"" + tag + "\",\"connectorId\":\"" + connector + "\",\"startValue\":\"" + energy_start + "\",\"startValueTimestamp\":\"" + iat_start + "\",\"lastValue\":\"" + energy_last + "\",\"lastValueTimestamp\":\"" + iat_last + "\"}");
    }

    // @ApiOperation(httpMethod = "GET", value = "Stop a bunch of Transactions", notes = "INPUT = chargeBoxId, Day, Month, Year \n\nDay/Month/Year = Actual date before which transactions are to be deleted. \n\nRETURN: List of Deleted Transaction.", tags = "1.0.1")
    // @ApiResponses(value = {
    //         @ApiResponse(code = 200, message = "OK"),
    //         @ApiResponse(code = 500, message = "Internal Error")
    // })
    // @GetMapping(value = sCHARGEBOXID + "/stopBunchTransaction/{day}/{month}/{year}")
    // public void stopBunchTransaction(@PathVariable("chargeBoxId") String chargeBoxId,
    //                                     @PathVariable("day") int switchOffDay,
    //                                     @PathVariable("month") int switchOffMonth,
    //                                     @PathVariable("year") int switchOffYear,
    //                                   HttpServletResponse response) throws IOException {
    //     try{
    //         int startingMonth,startingDay,startingYear;
    //         /*Calendar calendar = Calendar.getInstance();
    //         int currentYear = calendar.get(Calendar.YEAR);
    //         int currentMonth = calendar.get(Calendar.MONTH)+1;
    //         int currentDay = calendar.get(Calendar.DATE);*/
    //         RemoteStopTransactionParams params = new RemoteStopTransactionParams();
    //         List<Integer> transactionIDs = transactionRepository.getActiveTransactionIds(chargeBoxId);

    //         JSONObject response_object = new JSONObject();
    //         for (int i = 0; i < transactionIDs.size(); i++) {
    //             TransactionDetails thisTxDetails = transactionRepository.getDetails(transactionIDs.get(i));
    //             Transaction thisTx = thisTxDetails.getTransaction();
    //             startingYear = thisTx.getStartTimestampDT().year().get();
    //             startingMonth = thisTx.getStartTimestampDT().monthOfYear().get();
    //             startingDay = thisTx.getStartTimestampDT().dayOfMonth().get();
    //             if (transactionIDs.get(i) != 323) {
    //                 if (startingYear < switchOffYear) {
    //                             response_object.put(String.valueOf(i),transactionIDs.get(i));
    //                             transactionStopService.stop(transactionIDs.get(i));
    //                 }
    //                 else if (startingYear == switchOffYear) {
    //                     if (startingMonth < switchOffMonth) {
    //                         response_object.put(String.valueOf(i),transactionIDs.get(i));
    //                         transactionStopService.stop(transactionIDs.get(i));
    //                     }
    //                     else if (startingMonth == switchOffMonth) {
    //                         if (startingDay < switchOffDay) {
    //                             response_object.put(String.valueOf(i),transactionIDs.get(i));
    //                             transactionStopService.stop(transactionIDs.get(i));
    //                         }
    //                     }
    //                 }
    //             }
    //         }
    //         response_object.put("Task: ","Transaction Deletion");
    //         response_object.put("Task status: ", "Completed");
    //         response_object.put("chargeBoxId: ",chargeBoxId);

    //         writeOutput(response, response_object.toString());

    //     } catch (NullPointerException nullPointerException) {
    //         response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    //         JSONObject response_object = new JSONObject();
    //         response_object.put("chargeBoxId", chargeBoxId);
    //         response_object.put("connectorId", 2);
    //         response_object.put("tag", "unknown");
    //         response_object.put("status", "failed");
    //         response_object.put("response", "Request invalid");
    //         writeOutput(response, response_object.toString());

    //     }
    // }

    @ApiOperation(httpMethod = "GET", value = "Get Meter Reading of a specific transaction", notes = "INPUT = chargeBoxId, Day, Month, Year \n\nDay/Month/Year = Actual date before which transactions are to be deleted. \n\nRETURN: List of Deleted Transaction.", tags = "1.0.1")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 500, message = "Internal Error")
    })
    @GetMapping(value = "/getConsumption/{transactionId}")
    public void getConsumption(@PathVariable("transactionId") String transactionId,
                                        HttpServletResponse response) throws IOException {
        try{
            TransactionDetails thisTxDetails = transactionRepository.getDetails(Integer.parseInt(transactionId));
            List<TransactionDetails.MeterValues> intermediateValues = thisTxDetails.getValues();
            Transaction thisTx = thisTxDetails.getTransaction();
            JSONObject response_object = new JSONObject();
            String measurandValue = new String("Energy.Active.Import.Register");
            String energy_start = thisTx.getStartValue();
            for (int intermediateValue = (intermediateValues.size() - 1); intermediateValue != -1; --intermediateValue) {
                if (measurandValue.equals(intermediateValues.get(intermediateValue).getMeasurand())) {
                    int meterReading = Integer.parseInt(intermediateValues.get(intermediateValue).getValue()) - Integer.parseInt(energy_start);
                    response_object.put("consumption", meterReading);
                    response_object.put("lastValue", intermediateValues.get(intermediateValue).getValue());
                    break;
                }
            }
            response_object.put("transactionId", transactionId);
            response_object.put("startValue", energy_start);
            writeOutput(response, response_object.toString());
        } catch (NullPointerException nullPointerException) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            JSONObject response_object = new JSONObject();
            response_object.put("status", "failed");
            response_object.put("response", "Request invalid");
            writeOutput(response, response_object.toString());
        }
    }

    // @ApiOperation(httpMethod = "GET", value = "RemotelyStop a transaction.", notes = "INPUT: chargeBoxId ,Transaction ID \n\nRETURN: Details about Deleted Transaction. ", tags = "1.0.1")
    // @ApiResponses(value = {
            // @ApiResponse(code = 200, message = "OK"),
            // @ApiResponse(code = 500, message = "Internal Error")
    // })
    // @GetMapping(value = "/stopTransaction/{transactionId}")
    // public void stopRemoteTransaction(@PathVariable("chargeBoxId") String chargeBoxId,
    //                                   @PathVariable("transactionId") String transactionId,
    //                                   HttpServletResponse response) throws IOException {
    //     try {
    //         RemoteStopTransactionParams params = new RemoteStopTransactionParams();
    //         List<Integer> transactionIDs = transactionRepository.getActiveTransactionIds(chargeBoxId);
    //         if (transactionIDs.size() > 0) {
    //             if (transactionIDs.get(transactionIDs.size() - 1) == Integer.parseInt(transactionId)) {
    //                     params.setTransactionId(Integer.parseInt(transactionId));
    //                     List<ChargePointSelect> cp = new ArrayList<>();
    //                     ChargePointSelect cps = new ChargePointSelect(OcppTransport.JSON, chargeBoxId);
    //                     cp.add(cps);
    //                     params.setChargePointSelectList(cp);
    //                     CommunicationTask task = taskStore.get(client16.remoteStopTransaction(params));
    //                     while (!task.isFinished() || task.getResultMap().size() > 1) {
    //                         // System.out.println("[stopSession] wait for");
    //                     }
    //                     RequestResult result = (RequestResult) task.getResultMap().get(chargeBoxId);
    //                     transactionStopService.stop(transactionIDs);
    //                     // writeOutput(response, objectMapper.writeValueAsString(result.getResponse()));

    //    //                     JSONObject response_object = new JSONObject();
    //                     response_object.put("chargeBoxId", chargeBoxId);
    //                     response_object.put("connectorId", 2);
    //                     response_object.put("transactionId",transactionId);
    //                     response_object.put("status", "created");
    //                     response_object.put("response", objectMapper.writeValueAsString(result.getResponse()));
    //                     writeOutput(response, response_object.toString());
    //    //                     //writeOutput(response, "{\"chargeBoxId\":\"" + chargeBoxId + "\",\"connectorId\":\"2\",\"transactionId\":\"" + transactionId + "\",\"status\":\"created\",\"response\":" + objectMapper.writeValueAsString(result.getResponse()) + "}");

    //                 } else {
    //                 response.setStatus(HttpServletResponse.SC_CONFLICT);
    //    //                 JSONObject response_object = new JSONObject();
    //                 response_object.put("chargeBoxId", chargeBoxId);
    //                 response_object.put("connectorId", 2);
    //                 response_object.put("transactionId", transactionId);
    //                 response_object.put("status", "failed");
    //                 response_object.put("response", "Transaction-Charger mismatched");
    //                 writeOutput(response, response_object.toString());
    //    //                 //writeOutput(response, "{\"chargeBoxId\":\"" + chargeBoxId + "\",\"connectorId\":\"2\",\"transactionId\":\"" + transactionId + "\",\"status\":\"failed\",\"response\":\"Transaction-Charger mismatched\"}");
    //             }
    //         } else {
    //             response.setStatus(HttpServletResponse.SC_CONFLICT);
    //    //             JSONObject response_object = new JSONObject();
    //             response_object.put("chargeBoxId", chargeBoxId);
    //             response_object.put("connectorId", 2);
    //             response_object.put("transactionId", transactionId);
    //             response_object.put("status", "failed");
    //             response_object.put("response", "Transactions inactive");
    //             writeOutput(response, response_object.toString());
    //    //             writeOutput(response, "{\"chargeBoxId\":\"" + chargeBoxId + "\",\"connectorId\":\"2\",\"transactionId\":\"" + transactionId + "\",\"status\":\"failed\",\"response\":\"Transactions inactive\"}");
    //         }
    //     } catch (NullPointerException nullPointerException) {
    //         response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    //    //         JSONObject response_object = new JSONObject();
    //         response_object.put("chargeBoxId", chargeBoxId);
    //         response_object.put("connectorId", 2);
    //         response_object.put("tag", "unknown");
    //         response_object.put("status", "failed");
    //         response_object.put("response", "Request invalid");
    //         writeOutput(response, response_object.toString());
    //    //         //writeOutput(response, "{\"chargeBoxId\":\"" + chargeBoxId + "\",\"connectorId\":\"2\",\"tag\":\"unknown\",\"status\":\"failed\",\"response\":\"Request invalid\"}");
    //     }
    // }

    @ApiOperation(httpMethod = "GET", value = "RemotelyStop a transaction.", notes = "INPUT: chargeBoxId ,Transaction ID \n\nRETURN: Details about Deleted Transaction. ", tags = "1.0.1")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 500, message = "Internal Error")
    })
    @GetMapping(value = sCHARGEBOXID + "/stopTransaction/{transactionId}")
    public void stopRemoteTransaction0(@PathVariable("chargeBoxId") String chargeBoxId,
                                      @PathVariable("transactionId") String transactionId,
                                      HttpServletResponse response) throws IOException {
        try {
            RemoteStopTransactionParams params = new RemoteStopTransactionParams();
            List<Integer> transactionIDs = transactionRepository.getActiveTransactionIds(chargeBoxId);
            if (transactionIDs.size() > 0) {
                if (transactionIDs.get(transactionIDs.size() - 1) == Integer.parseInt(transactionId)) {
                        params.setTransactionId(Integer.parseInt(transactionId));
                        List<ChargePointSelect> cp = new ArrayList<>();
                        ChargePointSelect cps = new ChargePointSelect(OcppTransport.JSON, chargeBoxId);
                        cp.add(cps);
                        params.setChargePointSelectList(cp);
                        CommunicationTask task = taskStore.get(client16.remoteStopTransaction(params));
                        while (!task.isFinished() || task.getResultMap().size() > 1) {
                            // System.out.println("[stopSession] wait for");
                        }
                        RequestResult result = (RequestResult) task.getResultMap().get(chargeBoxId);
                        transactionStopService.stop(transactionIDs);
                        JSONObject response_object = new JSONObject();
                        response_object.put("chargeBoxId", chargeBoxId);
                        response_object.put("connectorId", 2);
                        response_object.put("transactionId", transactionId);
                        response_object.put("status", "created");
                        response_object.put("response", objectMapper.writeValueAsString(result.getResponse()));
                        writeOutput(response, response_object.toString());
                    } else {
                    response.setStatus(HttpServletResponse.SC_CONFLICT);
                    JSONObject response_object = new JSONObject();
                    response_object.put("chargeBoxId", chargeBoxId);
                    response_object.put("connectorId", 2);
                    response_object.put("transactionId", transactionId);
                    response_object.put("status", "failed");
                    response_object.put("response", "Transaction-Charger mismatched");
                    writeOutput(response, response_object.toString());
                }
            } else {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                JSONObject response_object = new JSONObject();
                response_object.put("chargeBoxId", chargeBoxId);
                response_object.put("connectorId", 2);
                response_object.put("transactionId", transactionId);
                response_object.put("status", "failed");
                response_object.put("response", "Transactions inactive");
                writeOutput(response, response_object.toString());
            }
        } catch (NullPointerException nullPointerException) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            JSONObject response_object = new JSONObject();
            response_object.put("chargeBoxId", chargeBoxId);
            response_object.put("connectorId", 2);
            response_object.put("tag", "unknown");
            response_object.put("status", "failed");
            response_object.put("response", "Request invalid");
            writeOutput(response, response_object.toString());
        }
    }

    @ApiOperation(httpMethod = "GET", value = "Remotely Stop a session.", notes = "INPUT: chargeBoxId, Tag Id. \n\nRETURN: Stopped Session's Info.", tags = "1.0.1")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 500, message = "Internal Error")
    })
    @GetMapping(value = sCHARGEBOXID + "/stopSession")
    public void stopRemoteSession0(@PathVariable("chargeBoxId") String chargeBoxId,
                                  HttpServletResponse response) throws IOException {
        try {
            RemoteStopTransactionParams params = new RemoteStopTransactionParams();
            List<Integer> transactionIDs = transactionRepository.getActiveTransactionIds(chargeBoxId);
            if (transactionIDs.size() > 0) {
                List<String> tokenList = new ArrayList<>();
                getTokenList("root").forEach(token -> tokenList.add(token.get(0)));
                if (tokenList.contains(transactionRepository.getDetails(transactionIDs.get(transactionIDs.size() - 1)).getTransaction().getOcppIdTag())) {
                    params.setTransactionId(transactionIDs.get(transactionIDs.size() - 1));
                    List<ChargePointSelect> cp = new ArrayList<>();
                    ChargePointSelect cps = new ChargePointSelect(OcppTransport.JSON, chargeBoxId);
                    cp.add(cps);
                    params.setChargePointSelectList(cp);
                    CommunicationTask task = taskStore.get(client16.remoteStopTransaction(params));
                    while (!task.isFinished() || task.getResultMap().size() > 1) {
                        // System.out.println("[stopSession] wait for");
                    }
                    RequestResult result = (RequestResult) task.getResultMap().get(chargeBoxId);
                    transactionStopService.stop(transactionIDs);
                    JSONObject response_object = new JSONObject();
                    response_object.put("chargeBoxId", chargeBoxId);
                    response_object.put("connectorId", 2);
                    response_object.put("tag", "root");
                    response_object.put("status", "created");
                    response_object.put("response", objectMapper.writeValueAsString(result.getResponse()));
                    writeOutput(response, response_object.toString());
                } else {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    JSONObject response_object = new JSONObject();
                    response_object.put("chargeBoxId", chargeBoxId);
                    response_object.put("connectorId", 2);
                    response_object.put("tag", "root");
                    response_object.put("status", "failed");
                    response_object.put("response", "Transaction-Tag mismatched");
                    writeOutput(response, response_object.toString());
                }
            } else {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.setHeader("Access-Control-Allow-Origin", "*");
                JSONObject response_object = new JSONObject();
                response_object.put("chargeBoxId", chargeBoxId);
                response_object.put("connectorId", 2);
                response_object.put("tag", "root");
                response_object.put("status", "failed");
                response_object.put("response", "Transactions inactive");
                writeOutput(response, response_object.toString());
            }
        } catch (NullPointerException nullPointerException) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            JSONObject response_object = new JSONObject();
            response_object.put("chargeBoxId", chargeBoxId);
            response_object.put("connectorId", 2);
            response_object.put("tag", "root");
            response_object.put("status", "failed");
            response_object.put("response", "Request invalid");
            writeOutput(response, response_object.toString());
        }
    }

    @ApiOperation(httpMethod = "GET", value = "Remotely Stop a session.", notes = "INPUT: chargeBoxId, Tag Id. \n\nRETURN: Stopped Session's Info.", tags = "1.0.1")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 500, message = "Internal Error")
    })
    @GetMapping(value = sCHARGEBOXID + "/stopSession/{ocpp_parent}")
    public void stopRemoteSession(@PathVariable("chargeBoxId") String chargeBoxId,
                                  @PathVariable("ocpp_parent") String ocpp_parent,
                                  HttpServletResponse response) throws IOException {
        try {
            RemoteStopTransactionParams params = new RemoteStopTransactionParams();
            List<Integer> transactionIDs = transactionRepository.getActiveTransactionIds(chargeBoxId);
            if (transactionIDs.size() > 0) {
                List<String> tokenList = new ArrayList<>();
                getTokenList(ocpp_parent).forEach(token -> tokenList.add(token.get(0)));
                if (tokenList.contains(transactionRepository.getDetails(transactionIDs.get(transactionIDs.size() - 1)).getTransaction().getOcppIdTag())) {
                    params.setTransactionId(transactionIDs.get(transactionIDs.size() - 1));
                    List<ChargePointSelect> cp = new ArrayList<>();
                    ChargePointSelect cps = new ChargePointSelect(OcppTransport.JSON, chargeBoxId);
                    cp.add(cps);
                    params.setChargePointSelectList(cp);
                    CommunicationTask task = taskStore.get(client16.remoteStopTransaction(params));
                    while (!task.isFinished() || task.getResultMap().size() > 1) {
                        // System.out.println("[stopSession] wait for");
                    }
                    RequestResult result = (RequestResult) task.getResultMap().get(chargeBoxId);
                    transactionStopService.stop(transactionIDs);
                    JSONObject response_object = new JSONObject();
                    response_object.put("chargeBoxId", chargeBoxId);
                    response_object.put("connectorId", 2);
                    response_object.put("tag", ocpp_parent);
                    response_object.put("status", "created");
                    response_object.put("response", objectMapper.writeValueAsString(result.getResponse()));
                    writeOutput(response, response_object.toString());
                } else {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    JSONObject response_object = new JSONObject();
                    response_object.put("chargeBoxId", chargeBoxId);
                    response_object.put("connectorId", 2);
                    response_object.put("tag", ocpp_parent);
                    response_object.put("status", "failed");
                    response_object.put("response", "Transaction-Tag mismatched");
                    writeOutput(response, response_object.toString());
                }
            } else {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.setHeader("Access-Control-Allow-Origin", "*");
                // writeOutput(response, objectMapper.writeValueAsString("No active transaction"));

                JSONObject response_object = new JSONObject();
                response_object.put("chargeBoxId", chargeBoxId);
                response_object.put("connectorId", 2);
                response_object.put("tag", ocpp_parent);
                response_object.put("status", "failed");
                response_object.put("response", "Transactions inactive");
                writeOutput(response, response_object.toString());

                //writeOutput(response, "{\"chargeBoxId\":\"" + chargeBoxId + "\",\"connectorId\":\"2\",\"tag\":\"" + ocpp_parent + "\",\"status\":\"failed\",\"response\":\"Transactions inactive\"}");
            }
        } catch (NullPointerException nullPointerException) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);

            JSONObject response_object = new JSONObject();
            response_object.put("chargeBoxId", chargeBoxId);
            response_object.put("connectorId", 2);
            response_object.put("tag", ocpp_parent);
            response_object.put("status", "failed");
            response_object.put("response", "Request invalid");
            writeOutput(response, response_object.toString());

            //writeOutput(response, "{\"chargeBoxId\":\"" + chargeBoxId + "\",\"connectorId\":\"2\",\"tag\":\"" + ocpp_parent + "\",\"status\":\"failed\",\"response\":\"Request invalid\"}");
        }
    }

    @RequestMapping(method = RequestMethod.OPTIONS, value = "/**")
    public void manageOptions(HttpServletResponse response) throws IOException {
        writeOutput(response, "");
    }

    private List<List<String>> getTokenList(String ocpp_parent) throws NullPointerException {
        List<String> ocppTagList = ocppTagRepository.getIdTags()
                .stream()
                .filter(tag -> Objects.equals(ocppTagRepository.getParentIdtag(tag), ocpp_parent))
                .collect(Collectors.toList());
        if (!ocppTagList.contains(ocpp_parent)) {
            ocppTagList.add(0, ocpp_parent);
        }
        List<List<String>> responseList = new ArrayList<>();

        ocppTagList.forEach(tag -> {
            OcppTagQueryForm ocppTagQueryForm = new OcppTagQueryForm();
            ocppTagQueryForm.setIdTag(tag);
            String note;
            Optional<OcppTag.Overview> optionalOverview = ocppTagRepository.getOverview(ocppTagQueryForm).stream().findFirst();
            if (optionalOverview.isPresent()) {
                note = ocppTagRepository.getRecord(optionalOverview.get().getOcppTagPk()).getNote();
                if (note == null) {
                    note = "";
                }
                responseList.add(Stream.of(tag, note).collect(Collectors.toList()));
            } else {
                throw new NullPointerException();
            }

        });
        return responseList;
    }

    // private String serializeArray(Object object) {
    //     try {
    //         return objectMapper.writeValueAsString(object);
    //     } catch (JsonProcessingException e) {
    //         // As fallback return empty array, do not let the frontend hang
    //         log.error("Error occurred during serialization of response. Returning empty array instead!", e);
    //         return "[]";
    //     }
    // }

    /**
     * We want to handle this JSON conversion locally, and do not want to register an application-wide
     * HttpMessageConverter just for this little class. Otherwise, it might have unwanted side effects due to
     * different serialization/deserialization needs of different APIs.
     * <p>
     * That's why we are directly accessing the low-level HttpServletResponse and manually writing to output.
     */
    private void writeOutput(HttpServletResponse response, String str) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.getWriter().write(str);
    }

}
