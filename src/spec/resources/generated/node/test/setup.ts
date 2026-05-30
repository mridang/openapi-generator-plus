import * as fs from 'node:fs';

const config = JSON.parse(fs.readFileSync('/tmp/chasm-config.json', 'utf8'));
process.env.API_BASE_URL = config.baseUrl;
process.env.CHASM_HTTP_URL = config.chasmHttpUrl;
process.env.CHASM_HTTPS_URL = config.chasmHttpsUrl;
process.env.CHASM_INTERNAL_HTTP_URL = config.chasmInternalHttpUrl;
process.env.CHASM_INTERNAL_HTTPS_URL = config.chasmInternalHttpsUrl;
process.env.WIREMOCK_HTTPS_URL = config.wiremockHttpsUrl;
process.env.WIREMOCK_HTTP_URL = config.wiremockHttpUrl;
process.env.WIREMOCK_INTERNAL_HTTP_URL = config.wiremockInternalHttpUrl;
process.env.WIREMOCK_INTERNAL_HTTPS_URL = config.wiremockInternalHttpsUrl;
process.env.PROXY_URL = config.proxyUrl;
process.env.CA_CERT_PATH = config.caCertPath;
