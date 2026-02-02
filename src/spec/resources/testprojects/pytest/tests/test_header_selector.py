# coding: utf-8

"""
Swagger Petstore - OpenAPI 3.0

Unit tests for HeaderSelector.

These tests verify RFC 9110 compliant content negotiation with quality weights.
"""

import pytest
from petstore_client.header_selector import HeaderSelector


class TestIsJsonMime:
    """Tests for is_json_mime method."""

    @pytest.fixture
    def header_selector(self):
        return HeaderSelector()

    def test_should_return_true_for_application_json(self, header_selector):
        assert header_selector.is_json_mime("application/json") is True

    def test_should_return_true_for_application_json_with_charset(self, header_selector):
        assert header_selector.is_json_mime("application/json; charset=UTF-8") is True

    def test_should_return_true_for_uppercase_application_json(self, header_selector):
        assert header_selector.is_json_mime("APPLICATION/JSON") is True

    def test_should_return_true_for_vendor_json_types(self, header_selector):
        assert header_selector.is_json_mime("application/vnd.api+json") is True
        assert header_selector.is_json_mime("application/vnd.company+json") is True
        assert header_selector.is_json_mime("application/hal+json") is True

    def test_should_return_false_for_text_html(self, header_selector):
        assert header_selector.is_json_mime("text/html") is False

    def test_should_return_false_for_application_xml(self, header_selector):
        assert header_selector.is_json_mime("application/xml") is False

    def test_should_return_false_for_none(self, header_selector):
        assert header_selector.is_json_mime(None) is False

    def test_should_return_false_for_empty_string(self, header_selector):
        assert header_selector.is_json_mime("") is False


class TestSelectAcceptHeader:
    """Tests for _select_accept_header method."""

    @pytest.fixture
    def header_selector(self):
        return HeaderSelector()

    def test_should_return_none_for_none_input(self, header_selector):
        assert header_selector._select_accept_header(None) is None

    def test_should_return_none_for_empty_list(self, header_selector):
        assert header_selector._select_accept_header([]) is None

    def test_should_return_single_accept_as_is(self, header_selector):
        assert header_selector._select_accept_header(["application/json"]) == "application/json"

    def test_should_return_single_non_json_accept_as_is(self, header_selector):
        assert header_selector._select_accept_header(["text/html"]) == "text/html"

    def test_should_return_comma_separated_list_when_no_json_types(self, header_selector):
        result = header_selector._select_accept_header(["text/html", "text/plain"])
        assert result == "text/html,text/plain"

    def test_should_prioritize_application_json_with_quality_weight(self, header_selector):
        result = header_selector._select_accept_header(["text/html", "application/json"])
        # application/json should come first with highest weight
        assert result.startswith("application/json")
        assert "text/html" in result

    def test_should_handle_multiple_json_types_with_priority(self, header_selector):
        result = header_selector._select_accept_header(
            ["text/html", "application/vnd.api+json", "application/json"]
        )
        # application/json should come first
        assert result.startswith("application/json")
        # application/vnd.api+json should come before text/html
        json_index = result.index("application/json")
        vendor_json_index = result.index("application/vnd.api+json")
        html_index = result.index("text/html")
        assert json_index < vendor_json_index
        assert vendor_json_index < html_index

    def test_should_filter_out_empty_entries(self, header_selector):
        result = header_selector._select_accept_header(["", "application/json", None])
        assert result == "application/json"

    def test_should_preserve_existing_quality_weights_in_order(self, header_selector):
        result = header_selector._select_accept_header(
            ["text/html;q=0.9", "application/json", "text/plain;q=0.8"]
        )
        # application/json should still come first (JSON priority)
        assert result.startswith("application/json")


class TestSelectHeaders:
    """Tests for select_headers method."""

    @pytest.fixture
    def header_selector(self):
        return HeaderSelector()

    def test_should_set_accept_header_when_accepts_provided(self, header_selector):
        headers = header_selector.select_headers(
            ["application/json"],
            "application/json",
            False
        )
        assert headers.get("Accept") == "application/json"

    def test_should_not_set_accept_header_when_accepts_empty(self, header_selector):
        headers = header_selector.select_headers(
            [],
            "application/json",
            False
        )
        assert headers.get("Accept") is None

    def test_should_set_content_type_header_when_not_multipart(self, header_selector):
        headers = header_selector.select_headers(
            ["application/json"],
            "application/json",
            False
        )
        assert headers.get("Content-Type") == "application/json"

    def test_should_not_set_content_type_header_when_multipart(self, header_selector):
        headers = header_selector.select_headers(
            ["application/json"],
            "application/json",
            True
        )
        assert headers.get("Content-Type") is None

    def test_should_default_content_type_to_application_json_when_empty(self, header_selector):
        headers = header_selector.select_headers(
            ["application/json"],
            "",
            False
        )
        assert headers.get("Content-Type") == "application/json"

    def test_should_default_content_type_to_application_json_when_none(self, header_selector):
        headers = header_selector.select_headers(
            ["application/json"],
            None,
            False
        )
        assert headers.get("Content-Type") == "application/json"


class TestGetNextWeight:
    """Tests for get_next_weight method."""

    @pytest.fixture
    def header_selector(self):
        return HeaderSelector()

    def test_should_return_standard_weight_sequence(self, header_selector):
        # Starting from 1000, should get: 1000, 900, 800, 700, ...
        assert header_selector.get_next_weight(1000, False) == 900
        assert header_selector.get_next_weight(900, False) == 800
        assert header_selector.get_next_weight(800, False) == 700
        assert header_selector.get_next_weight(700, False) == 600
        assert header_selector.get_next_weight(600, False) == 500
        assert header_selector.get_next_weight(500, False) == 400
        assert header_selector.get_next_weight(400, False) == 300
        assert header_selector.get_next_weight(300, False) == 200
        assert header_selector.get_next_weight(200, False) == 100
        # After 100, goes to 90, 80, ...
        assert header_selector.get_next_weight(100, False) == 90
        assert header_selector.get_next_weight(90, False) == 80

    def test_should_return_one_by_one_decrement_for_more_than_28_headers(self, header_selector):
        assert header_selector.get_next_weight(1000, True) == 999
        assert header_selector.get_next_weight(999, True) == 998
        assert header_selector.get_next_weight(998, True) == 997

    def test_should_return_one_when_weight_is_one_or_less(self, header_selector):
        assert header_selector.get_next_weight(1, False) == 1
        assert header_selector.get_next_weight(0, False) == 1
        assert header_selector.get_next_weight(-1, False) == 1

    def test_should_produce_exactly_27_steps(self, header_selector):
        # The formula should produce exactly 27 steps from 1000 to 1
        weight = 1000
        count = 0
        while weight > 1:
            weight = header_selector.get_next_weight(weight, False)
            count += 1
        # 1000 -> 900 -> 800 -> ... -> 100 -> 90 -> ... -> 10 -> 9 -> ... -> 1
        # That's 9 (1000 to 100) + 9 (100 to 10) + 9 (10 to 1) = 27 steps
        assert count == 27


class TestQualityWeightFormatting:
    """Tests for quality weight formatting."""

    @pytest.fixture
    def header_selector(self):
        return HeaderSelector()

    def test_should_not_add_quality_weight_for_weight_1000(self, header_selector):
        result = header_selector._select_accept_header(["application/json", "text/html"])
        # First header should not have ;q= because it's weight 1000
        assert result.startswith("application/json,") or result == "application/json"

    def test_should_format_quality_weight_correctly(self, header_selector):
        result = header_selector._select_accept_header(["application/json", "text/html"])
        # text/html should have quality weight like ;q=0.9
        assert "text/html;q=0.9" in result or "text/html;q=0." in result

    def test_should_remove_trailing_zeros_from_quality_weight(self, header_selector):
        result = header_selector._select_accept_header(["application/json", "text/html"])
        # Should be ;q=0.9 not ;q=0.900
        assert ";q=0.900" not in result
