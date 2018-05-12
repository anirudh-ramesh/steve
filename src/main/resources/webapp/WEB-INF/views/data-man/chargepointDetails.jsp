<%@ include file="../00-header.jsp" %>
<spring:hasBindErrors name="chargePointForm">
    <div class="error">
        Error while trying to update a charge point:
        <ul>
            <c:forEach var="error" items="${errors.allErrors}">
                <li>${error.defaultMessage}</li>
            </c:forEach>
        </ul>
    </div>
</spring:hasBindErrors>
<div class="content"><div>
    <section><span>
        Charge Point Details
        <a class="tooltip" href="#"><img src="${ctxPath}/static/images/info.png" style="vertical-align:middle">
            <span>Read-only fields are updated by the charge point.</span>
        </a>
    </span></section>

        <table class="userInput">
            <thead><tr><th>See Operations</th><th></th></thead>
            <tbody>
            <tr>
                <td>Transactions:</td>
                <td>
                    <a href="${ctxPath}/manager/transactions/query?chargeBoxId=${chargePointForm.chargeBoxId}&amp;type=ACTIVE">ACTIVE</a>
                     /
                    <a href="${ctxPath}/manager/transactions/query?chargeBoxId=${chargePointForm.chargeBoxId}&amp;type=ALL">ALL</a>
                </td>
            </tr>
            <tr>
                <td>Reservations:</td>
                <td>
                    <a href="${ctxPath}/manager/reservations/query?chargeBoxId=${chargePointForm.chargeBoxId}&amp;periodType=ACTIVE">ACTIVE</a>
                </td>
            </tr>
            </tbody>
        </table>

        <form:form action="${ctxPath}/manager/chargepoints/update" modelAttribute="chargePointForm">

            <form:hidden path="chargeBoxPk" readonly="true"/>
            <table class="userInput">
                <thead><tr><th>OCPP</th><th></th></thead>
                <tbody>
                    <tr>
                        <td>ChargeBox ID:</td>
                        <td>
                            <form:input path="chargeBoxId" readonly="true" />
                            <a class="tooltip" href="#"><img src="${ctxPath}/static/images/info.png" style="vertical-align:middle">
                                <span>This field is set when adding a charge point, and cannot be changed later</span>
                            </a>
                        </td>
                    </tr>
                    <tr><td>Endpoint Address:</td><td>${cp.chargeBox.endpointAddress}</td></tr>
                    <tr><td>Ocpp Protocol:</td><td>${cp.chargeBox.ocppProtocol}</td></tr>
                    <tr><td>Charge Point Vendor:</td><td>${cp.chargeBox.chargePointVendor}</td></tr>
                    <tr><td>Charge Point Model:</td><td>${cp.chargeBox.chargePointModel}</td></tr>
                    <tr><td>Charge Point Serial Number:</td><td>${cp.chargeBox.chargePointSerialNumber}</td></tr>
                    <tr><td>Charge Box Serial Number:</td><td>${cp.chargeBox.chargePointSerialNumber}</td></tr>
                    <tr><td>Firmware Version:</td><td>${cp.chargeBox.fwVersion}</td></tr>
                    <tr><td>Firmware Update Timestamp:</td><td>${cp.chargeBox.fwUpdateTimestamp}</td></tr>
                    <tr><td>Iccid:</td><td>${cp.chargeBox.iccid}</td></tr>
                    <tr><td>Imsi:</td><td>${cp.chargeBox.imsi}</td></tr>
                    <tr><td>Meter Type:</td><td>${cp.chargeBox.meterType}</td></tr>
                    <tr><td>Meter Serial Number:</td><td>${cp.chargeBox.meterSerialNumber}</td></tr>
                    <tr><td>Diagnostics Status:</td><td>${cp.chargeBox.diagnosticsStatus}</td></tr>
                    <tr><td>Diagnostics Timestamp:</td><td>${cp.chargeBox.diagnosticsTimestamp}</td></tr>
                    <tr><td>Last Hearbeat Timestamp:</td><td>${cp.chargeBox.lastHeartbeatTimestamp}</td></tr>
                </tbody>
            </table>

            <form:hidden path="address.addressPk" readonly="true"/>
            <%@ include file="00-address.jsp" %>

            <c:set var="submitButtonName" value="update" />
            <c:set var="submitButtonValue" value="Update" />
            <%@ include file="00-cp-misc.jsp" %>

    </form:form>
</div></div>
<%@ include file="../00-footer.jsp" %>
