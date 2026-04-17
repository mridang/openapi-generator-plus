import * as fs from 'fs';

const config = JSON.parse(fs.readFileSync('/tmp/prism-config.json', 'utf8'));
process.env.API_BASE_URL = config.baseUrl;
process.env.WIREMOCK_HTTPS_URL = config.wiremockHttpsUrl;
process.env.WIREMOCK_HTTP_URL = config.wiremockHttpUrl;
process.env.WIREMOCK_INTERNAL_HTTP_URL = config.wiremockInternalHttpUrl;
process.env.WIREMOCK_INTERNAL_HTTPS_URL = config.wiremockInternalHttpsUrl;
process.env.PROXY_URL = config.proxyUrl;
process.env.CA_CERT_PATH = config.caCertPath;
