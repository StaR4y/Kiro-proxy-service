package xyz.star4y.kiroproxy.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import xyz.star4y.kiroproxy.common.ApiException;

class AccountImportParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AccountImportParser parser = new AccountImportParser();

    @Test
    void parsesKiroAccountManagerExportFormat() throws Exception {
        AccountDtos.ImportAccountsRequest request = parser.parse(objectMapper.readTree("""
            {
              "version": "0.12.155",
              "exportedAt": 1760000000000,
              "accounts": [
                {
                  "id": "acc-1",
                  "email": "user@example.com",
                  "idp": "BuilderId",
                  "profileArn": "arn:test",
                  "machineId": "machine-1",
                  "credentials": {
                    "accessToken": "access-1",
                    "refreshToken": "refresh-1",
                    "clientId": "client-1",
                    "clientSecret": "secret-1",
                    "region": "us-west-2",
                    "authMethod": "IdC",
                    "provider": "BuilderId"
                  },
                  "usage": {
                    "limit": 250000
                  }
                }
              ],
              "groups": [],
              "tags": []
            }
            """));

        AccountDtos.CreateAccountRequest account = request.accounts().get(0);
        assertThat(request.upsert()).isNull();
        assertThat(account.accountId()).isEqualTo("acc-1");
        assertThat(account.email()).isEqualTo("user@example.com");
        assertThat(account.accessToken()).isEqualTo("access-1");
        assertThat(account.refreshToken()).isEqualTo("refresh-1");
        assertThat(account.clientId()).isEqualTo("client-1");
        assertThat(account.clientSecret()).isEqualTo("secret-1");
        assertThat(account.region()).isEqualTo("us-west-2");
        assertThat(account.authMethod()).isEqualTo("idc");
        assertThat(account.provider()).isEqualTo("BuilderId");
        assertThat(account.profileArn()).isEqualTo("arn:test");
        assertThat(account.machineId()).isEqualTo("machine-1");
        assertThat(account.quotaLimit()).isEqualTo(250000L);
    }

    @Test
    void keepsLegacyImportWrapperCompatible() throws Exception {
        AccountDtos.ImportAccountsRequest request = parser.parse(objectMapper.readTree("""
            {
              "upsert": true,
              "accounts": [
                {
                  "accountId": "legacy-1",
                  "email": "legacy@example.com",
                  "accessToken": "access-legacy",
                  "region": "us-east-1",
                  "authMethod": "idc",
                  "provider": "BuilderID"
                }
              ]
            }
            """));

        AccountDtos.CreateAccountRequest account = request.accounts().get(0);
        assertThat(request.upsert()).isTrue();
        assertThat(account.accountId()).isEqualTo("legacy-1");
        assertThat(account.accessToken()).isEqualTo("access-legacy");
        assertThat(account.provider()).isEqualTo("BuilderId");
    }

    @Test
    void acceptsRawAccountArray() throws Exception {
        AccountDtos.ImportAccountsRequest request = parser.parse(objectMapper.readTree("""
            [
              {
                "email": "array@example.com",
                "accessToken": "access-array"
              }
            ]
            """));

        assertThat(request.accounts()).hasSize(1);
        assertThat(request.accounts().get(0).email()).isEqualTo("array@example.com");
        assertThat(request.accounts().get(0).accessToken()).isEqualTo("access-array");
    }

    @Test
    void rejectsMissingAccountsArray() throws Exception {
        assertThatThrownBy(() -> parser.parse(objectMapper.readTree("{}")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("accounts array");
    }
}
