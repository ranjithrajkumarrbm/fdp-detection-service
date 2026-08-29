package com.example.fraud.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FraudControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private String evaluate(String body) throws Exception {
        return mvc.perform(post("/api/v1/fraud/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void normalTransactionIsGood() throws Exception {
        String res = evaluate("""
                {"transactionId":"T-good-1","customerId":"CUST1001","type":"DEBIT_CARD","amount":1500}
                """);
        assertThat(json.readTree(res).get("decision").asText()).isEqualTo("GOOD");
    }

    @Test
    void highValueDebitCardIsBlocked() throws Exception {
        mvc.perform(post("/api/v1/fraud/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transactionId":"T-hv-1","customerId":"CUST1001","type":"DEBIT_CARD","amount":600000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("BLOCK"))
                .andExpect(jsonPath("$.triggeredRules[*].ruleId", hasItem("FDP-001")));
    }

    @Test
    void farAwayLocationIsBlocked() throws Exception {
        String res = evaluate("""
                {"transactionId":"T-loc-1","customerId":"CUST1001","type":"DEBIT_CARD","amount":900,
                 "location":{"latitude":51.5074,"longitude":-0.1278,"city":"London","country":"GB"}}
                """);
        JsonNode node = json.readTree(res);
        assertThat(node.get("decision").asText()).isEqualTo("BLOCK");
        assertThat(node.get("triggeredRules").toString()).contains("FDP-003");
    }

    @Test
    void velocityBurstEscalates() throws Exception {
        String last = "GOOD";
        for (int i = 1; i <= 8; i++) {
            String res = evaluate("""
                    {"transactionId":"T-vel-%d","customerId":"CUSTVELOCITY","type":"DEBIT_CARD","amount":100}
                    """.formatted(i));
            last = json.readTree(res).get("decision").asText();
        }
        assertThat(last).isEqualTo("BLOCK");
    }

    @Test
    void failedAttemptsThenSuccessIsChallenged() throws Exception {
        for (int i = 1; i <= 3; i++) {
            evaluate("""
                    {"transactionId":"T-fts-f%d","customerId":"CUSTFTS","type":"DEBIT_CARD","amount":250,"status":"FAILED"}
                    """.formatted(i));
        }
        String res = evaluate("""
                {"transactionId":"T-fts-ok","customerId":"CUSTFTS","type":"DEBIT_CARD","amount":250,"status":"SUCCESS"}
                """);
        JsonNode node = json.readTree(res);
        assertThat(node.get("decision").asText()).isIn("CHALLENGE", "BLOCK");
        assertThat(node.get("triggeredRules").toString()).contains("FDP-005");
    }

    @Test
    void suspiciousImpsTransferIsBlocked() throws Exception {
        String res = evaluate("""
                {"transactionId":"T-imps-1","customerId":"CUSTXFER","type":"IMPS","amount":480000,
                 "beneficiary":{"accountNumber":"90909090901","ifsc":"HDFC0001234","name":"Unknown Payee"}}
                """);
        JsonNode node = json.readTree(res);
        assertThat(node.get("decision").asText()).isEqualTo("BLOCK");
        assertThat(node.get("triggeredRules").toString()).contains("FDP-006");
    }

    @Test
    void invalidPayloadReturns400() throws Exception {
        mvc.perform(post("/api/v1/fraud/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"CUST1001","type":"DEBIT_CARD","amount":-5}
                                """))
                .andExpect(status().isBadRequest());
    }
}
