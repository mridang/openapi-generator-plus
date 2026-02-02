import os
import pytest


@pytest.fixture
def api_base_url():
    return os.environ.get('API_BASE_URL', 'http://localhost:4010')
