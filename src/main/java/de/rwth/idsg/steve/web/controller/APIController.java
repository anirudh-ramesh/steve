package de.rwth.idsg.steve.web.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @ApiOperation(httpMethod = "GET", value = "Start a charging transaction", notes = "", tags = {"transaction", "start", "2.0.0-rc1"})
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK"),
        @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 412, message = "Precondition Failed"),
        @ApiResponse(code = 500, message = "Internal Error")
    })
    @GetMapping(value = "/transaction/start")
    public void start_transaction(@RequestParam(name = "charger") String _charger, @RequestParam(name = "tag", defaultValue = "root") String _tag, @RequestParam(name = "connector", defaultValue = "1") String _connector, HttpServletResponse response) throws IOException {
        JSONObject response_payload_object = new JSONObject();
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
                    response_payload_object.put("charger", _charger);
                    response_payload_object.put("connector", _connector);
                    response_payload_object.put("tag", _tag);
                    response_payload_object.put("response", "Charger disconnected from the CMS");
                    writeOutput(response, response_payload_object.toString());
                } else if (!result.getResponse().equals("Accepted")) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response_payload_object.put("charger", _charger);
                    response_payload_object.put("connector", _connector);
                    response_payload_object.put("tag", _tag);
                    response_payload_object.put("response", objectMapper.writeValueAsString(result.getResponse()).replace("\"", ""));
                    writeOutput(response, response_payload_object.toString());
                } else {
                    response.setStatus(HttpServletResponse.SC_OK);
                    response_payload_object.put("charger", _charger);
                    response_payload_object.put("connector", _connector);
                    response_payload_object.put("tag", _tag);
                    response_payload_object.put("response", objectMapper.writeValueAsString(result.getResponse()).replace("\"", ""));
                    writeOutput(response, response_payload_object.toString());
                }
            }
        } catch (NullPointerException nullPointerException) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response_payload_object.put("charger", _charger);
            response_payload_object.put("connector", _connector);
            response_payload_object.put("tag", _tag);
            response_payload_object.put("response", "Request invalid");
            writeOutput(response, response_payload_object.toString());
        } catch (NumberFormatException numberFormatException) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response_payload_object.put("charger", _charger);
            response_payload_object.put("connector", _connector);
            response_payload_object.put("tag", _tag);
            response_payload_object.put("response", "Request invalid");
            writeOutput(response, response_payload_object.toString());
        }
    }

    @ApiOperation(httpMethod = "GET", value = "Stop a charging transaction", notes = "", tags = {"transaction", "stop", "2.0.0-rc1"})
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK"),
        @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 409, message = "Conflict"),
        @ApiResponse(code = 500, message = "Internal Error")
    })
    @GetMapping(value = "/transaction/stop")
    public void stop_transaction(@RequestParam(name = "transaction") String _transaction, HttpServletResponse response) throws IOException {
        JSONObject response_payload_object = new JSONObject();
        try {
            Transaction thisTx = transactionRepository.getDetails(Integer.parseInt(_transaction)).getTransaction();
            String _charger = thisTx.getChargeBoxId();
            Integer _connector = thisTx.getConnectorId();
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
                        response_payload_object.put("charger", _charger);
                        response_payload_object.put("connector", _connector.toString());
                        response_payload_object.put("transaction", _transaction);
                        response_payload_object.put("response", objectMapper.writeValueAsString(result.getResponse()).replace("\"", ""));
                        writeOutput(response, response_payload_object.toString());
                    } else {
                        response.setStatus(HttpServletResponse.SC_CONFLICT);
                        response_payload_object.put("charger", _charger);
                        response_payload_object.put("connector", _connector.toString());
                        response_payload_object.put("transaction", _transaction);
                        response_payload_object.put("response", "Transaction properties mismatched");
                        writeOutput(response, response_payload_object.toString());
                }
            } else {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response_payload_object.put("charger", _charger);
                response_payload_object.put("connector", _connector.toString());
                response_payload_object.put("transaction", _transaction);
                response_payload_object.put("response", "Transactions inactive");
                writeOutput(response, response_payload_object.toString());
            }
        } catch (NullPointerException nullPointerException) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response_payload_object.put("charger", "");
            response_payload_object.put("connector", "");
            response_payload_object.put("transaction", _transaction);
            response_payload_object.put("response", "Request invalid");
            writeOutput(response, response_payload_object.toString());
        } catch (NumberFormatException numberFormatException) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response_payload_object.put("charger", "");
            response_payload_object.put("connector", "");
            response_payload_object.put("transaction", _transaction);
            response_payload_object.put("response", "Request invalid");
            writeOutput(response, response_payload_object.toString());
        }
    }

    @ApiOperation(httpMethod = "GET", value = "View the properties of a charging transaction", notes = "", tags = {"transaction", "properties", "2.0.0-rc1"})
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK"),
        @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 500, message = "Internal Error")
    })
    @GetMapping(value = "/transaction")
    public void get_transaction(@ApiParam(required = true, value = "Serial Number of the transaction; '*' for all, '~' for active") @RequestParam(name = "transaction") String _transaction, HttpServletResponse response) throws IOException {
        JSONObject response_payload_object = new JSONObject();
        if (_transaction.equals("*")) {
            // JSONArray transaction_array = new JSONArray();
            // List<String> transactions = new ArrayList<>();
            // TransactionQueryForm params = new TransactionQueryForm();
            // params.setType(TransactionQueryForm.QueryType.ALL);
            // transactions = transactionRepository.getTransactions(params).stream().collect(Collectors.toList());
            // for (int transaction = 0; transaction < transactions.size(); ++transaction) {
            //     transaction_array.put(transactions.get(transaction));
            // }
            // response.setStatus(HttpServletResponse.SC_OK);
            // writeOutput(response, transaction_array.toString());
        } else if (_transaction.equals("~")) {
            response.setStatus(HttpServletResponse.SC_OK);
            writeOutput(response, "{}");
        } else {
            try {
                TransactionDetails thisTxDetails = transactionRepository.getDetails(Integer.parseInt(_transaction));
                List<TransactionDetails.MeterValues> intermediateValues = thisTxDetails.getValues();
                Transaction thisTx = thisTxDetails.getTransaction();
                String energy_start = thisTx.getStartValue();
                String energy_last = energy_start;
                String iat_last = thisTx.getStartTimestampDT().toString();
                Integer consumption = 0;
                for (int intermediateValue = (intermediateValues.size() - 1); intermediateValue != -1; --intermediateValue) {
                    if (intermediateValues.get(intermediateValue).getMeasurand().equals("Energy.Active.Import.Register")) {
                        consumption = Integer.parseInt(intermediateValues.get(intermediateValue).getValue()) - Integer.parseInt(energy_start);
                        energy_last = intermediateValues.get(intermediateValue).getValue().toString();
                        iat_last = intermediateValues.get(intermediateValue).getValueTimestamp().toString();
                        break;
                    }
                }
                response.setStatus(HttpServletResponse.SC_OK);
                response_payload_object.put("consumption", Integer.toString(consumption));
                response_payload_object.put("transaction", _transaction);
                response_payload_object.put("energy_start", energy_start);
                response_payload_object.put("iat_start", thisTx.getStartTimestampDT().toString());
                response_payload_object.put("energy_last", energy_last);
                response_payload_object.put("iat_last", iat_last);
                response_payload_object.put("charger", thisTx.getChargeBoxId());
                response_payload_object.put("connector", Integer.toString(thisTx.getConnectorId()));
                response_payload_object.put("tag", thisTx.getOcppIdTag());
                writeOutput(response, response_payload_object.toString());
            } catch (NullPointerException nullPointerException) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                writeOutput(response, "{}");
            } catch (NumberFormatException numberFormatException) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                writeOutput(response, "{}");
            }
        }
    }

    @ApiOperation(httpMethod = "GET", value = "View the properties of a charger", notes = "", tags = {"charger", "properties", "2.0.0-rc1"})
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK"),
        @ApiResponse(code = 404, message = "Not Found"),
        @ApiResponse(code = 500, message = "Internal Error")
    })
    @GetMapping(value = "/charger")
    public void get_charger(@ApiParam(required = true, value = "Name of the charger; '*' for all, '~' for active") @RequestParam(name = "charger") String _charger, HttpServletResponse response) throws IOException {
        JSONObject response_payload_object = new JSONObject();
        if (_charger.equals("*")) {
            JSONArray charger_array = new JSONArray();
            List<String> chargers = new ArrayList<>();
            chargers = chargePointRepository.getChargeBoxIds().stream().collect(Collectors.toList());
            for (int charger = 0; charger < chargers.size(); ++charger) {
                charger_array.put(chargers.get(charger));
            }
            response.setStatus(HttpServletResponse.SC_OK);
            writeOutput(response, charger_array.toString());
        } else if (_charger.equals("~")) {
            response.setStatus(HttpServletResponse.SC_OK);
            // List<Object> objList = new ArrayList<>();
            // chargePointHelperService.getOcppJsonStatus().forEach(js -> {
            //     List<String> strList = new ArrayList<>();
            //     List<ConnectorStatus> unfilteredList = chargePointRepository.getChargePointConnectorStatus();
            //     List<ConnectorStatus> filteredList = ConnectorStatusFilter.filterAndPreferZero(unfilteredList);
            //     strList.add(js.getChargeBoxId());
            //     strList.add(filteredList.stream().parallel().filter(cs -> js.getChargeBoxId().equals(cs.getChargeBoxId())).findAny().orElse(null).getStatus());
            //     objList.add(strList);
            // });
            // writeOutput(response, serializeArray(objList));
            JSONArray response_payload_array = new JSONArray();
            chargePointHelperService.getOcppJsonStatus().forEach(js -> {
                List<ConnectorStatus> unfilteredList = chargePointRepository.getChargePointConnectorStatus();
                List<ConnectorStatus> filteredList = ConnectorStatusFilter.filterAndPreferZero(unfilteredList);
                response_payload_object.put("charger", js.getChargeBoxId());
                response_payload_object.put("status", filteredList.stream().parallel().filter(cs -> js.getChargeBoxId().equals(cs.getChargeBoxId())).findAny().orElse(null).getStatus());
                response_payload_array.put(response_payload_object);
            });
            writeOutput(response, response_payload_array.toString());
        } else {
            List<String> chargerList = new ArrayList<>();
            chargerList.add(_charger);
            try {
                ChargePoint.Details cp = chargePointRepository.getDetails(chargePointRepository.getChargeBoxIdPkPair(chargerList).get(_charger));
                List<ConnectorStatus> unfilteredList = chargePointRepository.getChargePointConnectorStatus();
                List<ConnectorStatus> filteredList = ConnectorStatusFilter.filterAndPreferZero(unfilteredList);
                AddressRecord addressRecord = cp.getAddress();
                response_payload_object.put("name_charger", cp.getChargeBox().getChargeBoxId());
                try { response_payload_object.put("serialNumber_charger", cp.getChargeBox().getChargeBoxSerialNumber()); } catch (NullPointerException nullPointerException) {}
                try { response_payload_object.put("model_charger", cp.getChargeBox().getChargePointModel()); } catch (NullPointerException nullPointerException) {}
                try { response_payload_object.put("manufacturer_charger", cp.getChargeBox().getChargePointVendor()); } catch (NullPointerException nullPointerException) {}
                try { response_payload_object.put("serialNumber_meter", cp.getChargeBox().getMeterSerialNumber()); } catch (NullPointerException nullPointerException) {}
                try { response_payload_object.put("type_meter", cp.getChargeBox().getMeterType()); } catch (NullPointerException nullPointerException) {}
                try { response_payload_object.put("note", cp.getChargeBox().getNote()); } catch (NullPointerException nullPointerException) {}
                try { response_payload_object.put("description", cp.getChargeBox().getDescription()); } catch (NullPointerException nullPointerException) {}
                try { response_payload_object.put("latitude", cp.getChargeBox().getLocationLatitude()); } catch (NullPointerException nullPointerException) {}
                try { response_payload_object.put("longitude", cp.getChargeBox().getLocationLongitude()); } catch (NullPointerException nullPointerException) {}
                try { response_payload_object.put("street_address", addressRecord.getStreet()); } catch (NullPointerException nullPointerException) {}
                try { response_payload_object.put("number_address", addressRecord.getHouseNumber()); } catch (NullPointerException nullPointerException) {}
                try { response_payload_object.put("country_address", addressRecord.getCountry()); } catch (NullPointerException nullPointerException) {}
                try { response_payload_object.put("pincode_address", addressRecord.getZipCode()); } catch (NullPointerException nullPointerException) {}
                try { response_payload_object.put("city_address", addressRecord.getCity()); } catch (NullPointerException nullPointerException) {}
                try { response_payload_object.put("status", filteredList.stream().parallel().filter(cs -> _charger.equals(cs.getChargeBoxId())).findAny().orElse(null).getStatus()); } catch (NullPointerException nullPointerException) {}
                try { response_payload_object.put("heartbeat", cp.getChargeBox().getLastHeartbeatTimestamp()); } catch (NullPointerException nullPointerException) {}
                response.setStatus(HttpServletResponse.SC_OK);
                writeOutput(response, response_payload_object.toString());
            } catch (NullPointerException nullPointerException) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                writeOutput(response, "{}");
            }
        }
    }

    @ApiOperation(httpMethod = "GET", value = "View the properties of a charger's connector(s)", notes = "", tags = {"connector", "properties", "2.0.0-rc1"})
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK"),
        @ApiResponse(code = 500, message = "Internal Error")
    })
    @GetMapping(value = "/connector")
    public void get_connector(@ApiParam(required = true, value = "Name of the charger") @RequestParam(name = "charger") String _charger, HttpServletResponse response) throws IOException {
        List<Integer> connectors = chargePointRepository.getNonZeroConnectorIds(_charger);
        JSONArray connectors_array = new JSONArray();
        JSONObject response_payload_object = new JSONObject();
        for (int connector = 0; connector < connectors.size(); ++connector) {
            connectors_array.put(Integer.toString(connectors.get(connector)));
        }
        response_payload_object.put("charger", _charger);
        response_payload_object.put("count_connectors", Integer.toString(connectors.size()));
        response_payload_object.put("address_connectors", connectors_array);
        response.setStatus(HttpServletResponse.SC_OK);
        writeOutput(response, response_payload_object.toString());
    }

    @ApiOperation(httpMethod = "GET", value = "View the properties of a tag", notes = "", tags = {"tag", "properties", "2.0.0-rc1"})
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK"),
        @ApiResponse(code = 500, message = "Internal Error")
    })
    @GetMapping(value = "/tag")
    public void get_tag(@ApiParam(required = true, value = "Only '*' supported") @RequestParam(name = "tag") String _tag, HttpServletResponse response) throws IOException {
        if (_tag.equals("*")) {
            JSONArray tag_array = new JSONArray();
            List<String> tags = new ArrayList<>();
            tags = ocppTagRepository.getIdTags().stream().collect(Collectors.toList());
            for (int tag = 0; tag < tags.size(); ++tag) {
                tag_array.put(tags.get(tag));
            }
            response.setStatus(HttpServletResponse.SC_OK);
            writeOutput(response, tag_array.toString());
        } else {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            writeOutput(response, "{}");
        }
    }

// Code break added by Anirudh

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
                    JSONObject response_payload_object = new JSONObject();
                    response_payload_object.put("chargeBoxId", chargeBoxId);
                    // response_payload_object.put("connectorId", 2);
                    response_payload_object.put("connectorId", 1);
                    response_payload_object.put("tag", "root");
                    response_payload_object.put("status", "failed");
                    response_payload_object.put("response", "Charger disconnected from the CMS");
                    writeOutput(response, response_payload_object.toString());
                } else if (!result.getResponse().equals("Accepted")) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    JSONObject response_payload_object = new JSONObject();
                    response_payload_object.put("chargeBoxId", chargeBoxId);
                    // response_payload_object.put("connectorId", 2);
                    response_payload_object.put("connectorId", 1);
                    response_payload_object.put("tag", "root");
                    response_payload_object.put("status", "failed");
                    response_payload_object.put("response", objectMapper.writeValueAsString(result.getResponse()));
                    writeOutput(response, response_payload_object.toString());
                } else {
                    JSONObject response_payload_object = new JSONObject();
                    response_payload_object.put("chargeBoxId", chargeBoxId);
                    // response_payload_object.put("connectorId", 2);
                    response_payload_object.put("connectorId", 1);
                    response_payload_object.put("tag", "root");
                    response_payload_object.put("status", "created");
                    response_payload_object.put("response", "Accepted");
                    writeOutput(response, response_payload_object.toString());
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
                    JSONObject response_payload_object = new JSONObject();
                    response_payload_object.put("chargeBoxId", chargeBoxId);
                    // response_payload_object.put("connectorId", 2);
                    response_payload_object.put("connectorId", 1);
                    response_payload_object.put("tag", idTag);
                    response_payload_object.put("status", "failed");
                    response_payload_object.put("response", "Charger disconnected from the CMS");
                    writeOutput(response, response_payload_object.toString());
                } else if (!result.getResponse().equals("Accepted")) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    JSONObject response_payload_object = new JSONObject();
                    response_payload_object.put("chargeBoxId", chargeBoxId);
                    // response_payload_object.put("connectorId", 2);
                    response_payload_object.put("connectorId", 1);
                    response_payload_object.put("tag", idTag);
                    response_payload_object.put("status", "failed");
                    response_payload_object.put("response", objectMapper.writeValueAsString(result.getResponse()));
                    writeOutput(response, response_payload_object.toString());
                } else {
                    JSONObject response_payload_object = new JSONObject();
                    response_payload_object.put("chargeBoxId", chargeBoxId);
                    // response_payload_object.put("connectorId", 2);
                    response_payload_object.put("connectorId", 1);
                    response_payload_object.put("tag", idTag);
                    response_payload_object.put("status", "created");
                    response_payload_object.put("response", objectMapper.writeValueAsString(result.getResponse()));
                    writeOutput(response, response_payload_object.toString());
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
    //     // TransactionDetails thisTxDetails = new TransactionDetails();
    //     if (transactionIDs.size() > 0) {
    //         if (transactionIDs.get(transactionIDs.size() - 1) == Integer.parseInt(transactionId)) {
    //             TransactionDetails thisTxDetails = transactionRepository.getDetails(Integer.parseInt(transactionId));
    //             List<TransactionDetails.MeterValues> intermediateValues = thisTxDetails.getValues();
    //             System.out.println(intermediateValues.get(intermediateValues.size() - 1).getValue());
    //             writeOutput(response, "");
    //         } else {
    //             writeOutput(response, "");
    //         }
    //     } else {
    //         TransactionDetails thisTxDetails = transactionRepository.getDetails(Integer.parseInt(transactionId));
    //         List<TransactionDetails.MeterValues> intermediateValues = thisTxDetails.getValues();
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

        JSONObject response_payload_object = new JSONObject();
        response_payload_object.put("transactionId", transactionId);
        response_payload_object.put("chargeBoxId", charger);
        response_payload_object.put("tag", tag);
        response_payload_object.put("connectorId", connector);
        response_payload_object.put("startValue", energy_start);
        response_payload_object.put("startValueTimestamp", iat_start);
        response_payload_object.put("lastValue", energy_last);
        response_payload_object.put("lastValueTimestamp", iat_last);
        writeOutput(response, response_payload_object.toString());
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

    //         JSONObject response_payload_object = new JSONObject();
    //         for (int i = 0; i < transactionIDs.size(); i++) {
    //             TransactionDetails thisTxDetails = transactionRepository.getDetails(transactionIDs.get(i));
    //             Transaction thisTx = thisTxDetails.getTransaction();
    //             startingYear = thisTx.getStartTimestampDT().year().get();
    //             startingMonth = thisTx.getStartTimestampDT().monthOfYear().get();
    //             startingDay = thisTx.getStartTimestampDT().dayOfMonth().get();
    //             if (transactionIDs.get(i) != 323) {
    //                 if (startingYear < switchOffYear) {
    //                             response_payload_object.put(String.valueOf(i),transactionIDs.get(i));
    //                             transactionStopService.stop(transactionIDs.get(i));
    //                 }
    //                 else if (startingYear == switchOffYear) {
    //                     if (startingMonth < switchOffMonth) {
    //                         response_payload_object.put(String.valueOf(i),transactionIDs.get(i));
    //                         transactionStopService.stop(transactionIDs.get(i));
    //                     }
    //                     else if (startingMonth == switchOffMonth) {
    //                         if (startingDay < switchOffDay) {
    //                             response_payload_object.put(String.valueOf(i),transactionIDs.get(i));
    //                             transactionStopService.stop(transactionIDs.get(i));
    //                         }
    //                     }
    //                 }
    //             }
    //         }
    //         response_payload_object.put("Task: ","Transaction Deletion");
    //         response_payload_object.put("Task status: ", "Completed");
    //         response_payload_object.put("chargeBoxId: ",chargeBoxId);

    //         writeOutput(response, response_payload_object.toString());

    //     } catch (NullPointerException nullPointerException) {
    //         response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    //         JSONObject response_payload_object = new JSONObject();
    //         response_payload_object.put("chargeBoxId", chargeBoxId);
    //         response_payload_object.put("connectorId", 2);
    //         response_payload_object.put("tag", "unknown");
    //         response_payload_object.put("status", "failed");
    //         response_payload_object.put("response", "Request invalid");
    //         writeOutput(response, response_payload_object.toString());

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
            JSONObject response_payload_object = new JSONObject();
            String measurandValue = new String("Energy.Active.Import.Register");
            String energy_start = thisTx.getStartValue();
            for (int intermediateValue = (intermediateValues.size() - 1); intermediateValue != -1; --intermediateValue) {
                if (measurandValue.equals(intermediateValues.get(intermediateValue).getMeasurand())) {
                    int meterReading = Integer.parseInt(intermediateValues.get(intermediateValue).getValue()) - Integer.parseInt(energy_start);
                    response_payload_object.put("consumption", meterReading);
                    response_payload_object.put("lastValue", intermediateValues.get(intermediateValue).getValue());
                    break;
                }
            }
            response_payload_object.put("transactionId", transactionId);
            response_payload_object.put("startValue", energy_start);
            writeOutput(response, response_payload_object.toString());
        } catch (NullPointerException nullPointerException) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            JSONObject response_payload_object = new JSONObject();
            response_payload_object.put("status", "failed");
            response_payload_object.put("response", "Request invalid");
            writeOutput(response, response_payload_object.toString());
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

    //    //                     JSONObject response_payload_object = new JSONObject();
    //                     response_payload_object.put("chargeBoxId", chargeBoxId);
    //                     response_payload_object.put("connectorId", 2);
    //                     response_payload_object.put("transactionId",transactionId);
    //                     response_payload_object.put("status", "created");
    //                     response_payload_object.put("response", objectMapper.writeValueAsString(result.getResponse()));
    //                     writeOutput(response, response_payload_object.toString());
    //    //                     //writeOutput(response, "{\"chargeBoxId\":\"" + chargeBoxId + "\",\"connectorId\":\"2\",\"transactionId\":\"" + transactionId + "\",\"status\":\"created\",\"response\":" + objectMapper.writeValueAsString(result.getResponse()) + "}");

    //                 } else {
    //                 response.setStatus(HttpServletResponse.SC_CONFLICT);
    //    //                 JSONObject response_payload_object = new JSONObject();
    //                 response_payload_object.put("chargeBoxId", chargeBoxId);
    //                 response_payload_object.put("connectorId", 2);
    //                 response_payload_object.put("transactionId", transactionId);
    //                 response_payload_object.put("status", "failed");
    //                 response_payload_object.put("response", "Transaction-Charger mismatched");
    //                 writeOutput(response, response_payload_object.toString());
    //    //                 //writeOutput(response, "{\"chargeBoxId\":\"" + chargeBoxId + "\",\"connectorId\":\"2\",\"transactionId\":\"" + transactionId + "\",\"status\":\"failed\",\"response\":\"Transaction-Charger mismatched\"}");
    //             }
    //         } else {
    //             response.setStatus(HttpServletResponse.SC_CONFLICT);
    //    //             JSONObject response_payload_object = new JSONObject();
    //             response_payload_object.put("chargeBoxId", chargeBoxId);
    //             response_payload_object.put("connectorId", 2);
    //             response_payload_object.put("transactionId", transactionId);
    //             response_payload_object.put("status", "failed");
    //             response_payload_object.put("response", "Transactions inactive");
    //             writeOutput(response, response_payload_object.toString());
    //    //             writeOutput(response, "{\"chargeBoxId\":\"" + chargeBoxId + "\",\"connectorId\":\"2\",\"transactionId\":\"" + transactionId + "\",\"status\":\"failed\",\"response\":\"Transactions inactive\"}");
    //         }
    //     } catch (NullPointerException nullPointerException) {
    //         response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    //    //         JSONObject response_payload_object = new JSONObject();
    //         response_payload_object.put("chargeBoxId", chargeBoxId);
    //         response_payload_object.put("connectorId", 2);
    //         response_payload_object.put("tag", "unknown");
    //         response_payload_object.put("status", "failed");
    //         response_payload_object.put("response", "Request invalid");
    //         writeOutput(response, response_payload_object.toString());
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
                        JSONObject response_payload_object = new JSONObject();
                        response_payload_object.put("chargeBoxId", chargeBoxId);
                        response_payload_object.put("connectorId", 2);
                        response_payload_object.put("transactionId", transactionId);
                        response_payload_object.put("status", "created");
                        response_payload_object.put("response", objectMapper.writeValueAsString(result.getResponse()));
                        writeOutput(response, response_payload_object.toString());
                    } else {
                    response.setStatus(HttpServletResponse.SC_CONFLICT);
                    JSONObject response_payload_object = new JSONObject();
                    response_payload_object.put("chargeBoxId", chargeBoxId);
                    response_payload_object.put("connectorId", 2);
                    response_payload_object.put("transactionId", transactionId);
                    response_payload_object.put("status", "failed");
                    response_payload_object.put("response", "Transaction-Charger mismatched");
                    writeOutput(response, response_payload_object.toString());
                }
            } else {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                JSONObject response_payload_object = new JSONObject();
                response_payload_object.put("chargeBoxId", chargeBoxId);
                response_payload_object.put("connectorId", 2);
                response_payload_object.put("transactionId", transactionId);
                response_payload_object.put("status", "failed");
                response_payload_object.put("response", "Transactions inactive");
                writeOutput(response, response_payload_object.toString());
            }
        } catch (NullPointerException nullPointerException) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            JSONObject response_payload_object = new JSONObject();
            response_payload_object.put("chargeBoxId", chargeBoxId);
            response_payload_object.put("connectorId", 2);
            response_payload_object.put("tag", "unknown");
            response_payload_object.put("status", "failed");
            response_payload_object.put("response", "Request invalid");
            writeOutput(response, response_payload_object.toString());
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
                    JSONObject response_payload_object = new JSONObject();
                    response_payload_object.put("chargeBoxId", chargeBoxId);
                    response_payload_object.put("connectorId", 2);
                    response_payload_object.put("tag", "root");
                    response_payload_object.put("status", "created");
                    response_payload_object.put("response", objectMapper.writeValueAsString(result.getResponse()));
                    writeOutput(response, response_payload_object.toString());
                } else {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    JSONObject response_payload_object = new JSONObject();
                    response_payload_object.put("chargeBoxId", chargeBoxId);
                    response_payload_object.put("connectorId", 2);
                    response_payload_object.put("tag", "root");
                    response_payload_object.put("status", "failed");
                    response_payload_object.put("response", "Transaction-Tag mismatched");
                    writeOutput(response, response_payload_object.toString());
                }
            } else {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.setHeader("Access-Control-Allow-Origin", "*");
                JSONObject response_payload_object = new JSONObject();
                response_payload_object.put("chargeBoxId", chargeBoxId);
                response_payload_object.put("connectorId", 2);
                response_payload_object.put("tag", "root");
                response_payload_object.put("status", "failed");
                response_payload_object.put("response", "Transactions inactive");
                writeOutput(response, response_payload_object.toString());
            }
        } catch (NullPointerException nullPointerException) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            JSONObject response_payload_object = new JSONObject();
            response_payload_object.put("chargeBoxId", chargeBoxId);
            response_payload_object.put("connectorId", 2);
            response_payload_object.put("tag", "root");
            response_payload_object.put("status", "failed");
            response_payload_object.put("response", "Request invalid");
            writeOutput(response, response_payload_object.toString());
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
                    JSONObject response_payload_object = new JSONObject();
                    response_payload_object.put("chargeBoxId", chargeBoxId);
                    response_payload_object.put("connectorId", 2);
                    response_payload_object.put("tag", ocpp_parent);
                    response_payload_object.put("status", "created");
                    response_payload_object.put("response", objectMapper.writeValueAsString(result.getResponse()));
                    writeOutput(response, response_payload_object.toString());
                } else {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    JSONObject response_payload_object = new JSONObject();
                    response_payload_object.put("chargeBoxId", chargeBoxId);
                    response_payload_object.put("connectorId", 2);
                    response_payload_object.put("tag", ocpp_parent);
                    response_payload_object.put("status", "failed");
                    response_payload_object.put("response", "Transaction-Tag mismatched");
                    writeOutput(response, response_payload_object.toString());
                }
            } else {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.setHeader("Access-Control-Allow-Origin", "*");
                // writeOutput(response, objectMapper.writeValueAsString("No active transaction"));

                JSONObject response_payload_object = new JSONObject();
                response_payload_object.put("chargeBoxId", chargeBoxId);
                response_payload_object.put("connectorId", 2);
                response_payload_object.put("tag", ocpp_parent);
                response_payload_object.put("status", "failed");
                response_payload_object.put("response", "Transactions inactive");
                writeOutput(response, response_payload_object.toString());

                //writeOutput(response, "{\"chargeBoxId\":\"" + chargeBoxId + "\",\"connectorId\":\"2\",\"tag\":\"" + ocpp_parent + "\",\"status\":\"failed\",\"response\":\"Transactions inactive\"}");
            }
        } catch (NullPointerException nullPointerException) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);

            JSONObject response_payload_object = new JSONObject();
            response_payload_object.put("chargeBoxId", chargeBoxId);
            response_payload_object.put("connectorId", 2);
            response_payload_object.put("tag", ocpp_parent);
            response_payload_object.put("status", "failed");
            response_payload_object.put("response", "Request invalid");
            writeOutput(response, response_payload_object.toString());

            //writeOutput(response, "{\"chargeBoxId\":\"" + chargeBoxId + "\",\"connectorId\":\"2\",\"tag\":\"" + ocpp_parent + "\",\"status\":\"failed\",\"response\":\"Request invalid\"}");
        }
    }

    // @ApiOperation(httpMethod = "GET", value = "", notes = "INPUT: NULL", tags = "1.0.1")
    // @ApiResponses(value = {
    //         @ApiResponse(code = 200, message = "OK"),
    //         @ApiResponse(code = 500, message = "Internal Error")
    // })
    // @GetMapping("/user_login")
    // public void getUserDetails(@RequestParam("email") String email,
    //                            @RequestParam("id") String id,
    //                            HttpServletResponse response) throws IOException {
    //     Optional<User.Overview> user = userRepository
    //             .getOverview(new UserQueryForm())
    //             .stream()
    //             .parallel()
    //             .filter(usr -> usr.getEmail().equals(email))
    //             .findFirst();
    //     if (user.isPresent() && user.get().getOcppIdTag().equals(id)) {
    //         String s = serializeArray("true");
    //         writeOutput(response, s);
    //     } else {
    //         response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    //         writeOutput(response, serializeArray("false"));
    //     }

    // }

    // @PutMapping("/addToken")
    // public void putToken(@RequestParam("id") String ocpp_parent,
    //                      @RequestParam("token") String token,
    //                      @RequestParam(value = "note", required = false, defaultValue = " ") String note,
    //                      HttpServletResponse response) throws IOException {
    //     OcppTagForm newTag = new OcppTagForm();
    //     newTag.setIdTag(token);
    //     newTag.setParentIdTag(ocpp_parent);
    //     if (note == null) {
    //         note = "";
    //     }
    //     newTag.setNote(note);
    //     try {
    //         ocppTagRepository.addOcppTag(newTag);
    //         writeOutput(response, serializeArray("Ok"));
    //     } catch (Exception exception) {
    //         exception.printStackTrace();
    //         response.setStatus(HttpServletResponse.SC_CONFLICT);
    //         writeOutput(response, serializeArray("Could not add new token"));
    //     }
    // }

    // @ApiOperation(httpMethod = "GET", value = "", notes = "INPUT: Tag Id", tags = "1.0.1")
    // @ApiResponses(value = {
    //         @ApiResponse(code = 200, message = "OK"),
    //         @ApiResponse(code = 500, message = "Internal Error")
    // })
    // @GetMapping("/getTokens")
    // public void getTokens(@RequestParam("id") String ocpp_parent,
    //                       HttpServletResponse response) throws IOException {
    //     try {
    //         List<List<String>> responseList = getTokenList(ocpp_parent);
    //         writeOutput(response, serializeArray(responseList));
    //     } catch (NullPointerException nullPointerException) {
    //         response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    //     }
    // }

    // @DeleteMapping("/removeToken")
    // public void removeToken(@RequestParam("tokenID") String token,
    //                         HttpServletResponse response) throws IOException {
    //     Optional<OcppTag.Overview> ocppTag = ocppTagRepository
    //             .getOverview(new OcppTagQueryForm())
    //             .stream()
    //             .filter(o -> o.getIdTag().equals(token))
    //             .findFirst();
    //     // Only delete non parent ID tags
    //     if (ocppTag.isPresent() && ocppTag.get().getParentOcppTagPk() != null) {
    //         int ocppTagPk = ocppTag.get().getOcppTagPk();
    //         ocppTagRepository.deleteOcppTag(ocppTagPk);
    //         writeOutput(response, serializeArray("Ok, deleted."));
    //     } else {
    //         response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    //         writeOutput(response, serializeArray("Can't delete token."));
    //     }

    // }

    // @ApiOperation(httpMethod = "GET", value = "", notes = "description", tags = "1.0.1")
    // @ApiResponses(value = {
    //         @ApiResponse(code = 200, message = "OK"),
    //         @ApiResponse(code = 500, message = "Internal Error")
    // })
    // @GetMapping("/getStatistics")
    // public void getStatistics(@RequestParam("tokenID") String token, @RequestParam("period") TransactionQueryForm.QueryPeriodType period
    //         , @RequestParam(value = "allStatistics", required = false, defaultValue = "false") boolean allStatistics,
    //                           HttpServletResponse response) throws IOException {
    //     try {
    //         TransactionQueryForm params = new TransactionQueryForm();
    //         params.setPeriodType(period);
    //         params.setType(TransactionQueryForm.QueryType.ALL);
    //         List<String> ocppTagList = new ArrayList<>();
    //         if (allStatistics) {
    //             if (ocppTagRepository.getParentIdtag(token) != null) {
    //                 token = ocppTagRepository.getParentIdtag(token);
    //             }
    //             // Get all Transactions of the token
    //             // First get all Tags
    //             String finalToken = token;
    //             ocppTagList = ocppTagRepository.getIdTags()
    //                     .stream()
    //                     .filter(tag -> Objects.equals(ocppTagRepository.getParentIdtag(tag), finalToken))
    //                     .collect(Collectors.toList());
    //         }
    //         if (!ocppTagList.contains(token)) {
    //             ocppTagList.add(0, token);
    //         }
    //         Map<Integer, List<String>> transactionMap = new LinkedHashMap<>();
    //         for (String tag : ocppTagList) {
    //             params.setOcppIdTag(tag);
    //             Map<Integer, List<String>> finalTransactionMap = transactionMap;
    //             transactionRepository.getTransactions(params).stream()
    //                     .parallel()
    //                     .filter(transaction -> transaction.getStopTimestamp() != null && !transaction.getStopTimestamp().isEmpty())
    //                     .forEach(transaction -> {
    //                         List<String> transactionDetailList = new ArrayList<>();
    //                         transactionDetailList.add(String.valueOf(transaction.getId()));
    //                         transactionDetailList.add(transaction.getChargeBoxId());
    //                         AddressRecord addressRecord = chargePointRepository.getDetails(transaction.getChargeBoxPk()).getAddress();
    //                         String address = addressRecord.getStreet() + " " + addressRecord.getHouseNumber() + ", "
    //                                 + addressRecord.getCountry() + " " + addressRecord.getZipCode() + " " + addressRecord.getCity();
    //                         transactionDetailList.add(address);
    //                         transactionDetailList.add(transaction.getOcppIdTag());
    //                         transactionDetailList.add(transaction.getStartValue());
    //                         transactionDetailList.add(transaction.getStartTimestampDT().toString());
    //                         transactionDetailList.add(transaction.getStopTimestampDT().toString());
    //                         transactionDetailList.add(transaction.getStopReason());
    //                         transactionDetailList.add(transaction.getStopEventActor().toString());
    //                         transactionDetailList.add(transaction.getStopValue());
    //                         transactionRepository.getDetails(transaction
    //                                 .getId())
    //                                 .getValues()
    //                                 .stream()
    //                                 .parallel()
    //                                 .filter(meterValues -> meterValues.getUnit() != null && !meterValues.getUnit().isEmpty())
    //                                 .findFirst()
    //                                 .ifPresentOrElse((meterValues -> transactionDetailList.add(meterValues.getUnit())), () -> transactionDetailList.add(""));
    //                         finalTransactionMap.put(transaction.getId(), transactionDetailList);
    //                     });
    //         }
    //         transactionMap = transactionMap.entrySet()
    //                 .stream()
    //                 .parallel()
    //                 .sorted(Collections.reverseOrder(Map.Entry.comparingByKey()))
    //                 .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
    //                         (e1, e2) -> e2, LinkedHashMap::new));
    //         writeOutput(response, serializeArray(transactionMap));
    //     } catch (NullPointerException nullPointerException) {
    //         response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    //     } catch (Exception illegalArgumentException) {
    //         response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    //     }
    // }

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

    private String serializeArray(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            // As fallback return empty array, do not let the frontend hang
            log.error("Error occurred during serialization of response. Returning empty array instead!", e);
            return "[]";
        }
    }

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