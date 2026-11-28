defmodule PetstoreClient.EventStreamTest do
  use ExUnit.Case, async: true

  alias PetstoreClient.ByteStream
  alias PetstoreClient.Errors.ApiError
  alias PetstoreClient.Errors.SerializationError
  alias PetstoreClient.Errors.StreamError
  alias PetstoreClient.EventStream
  alias PetstoreClient.JsonLinesStream
  alias PetstoreClient.ServerSentEvent
  alias PetstoreClient.SseStream

  # The line splitter, the server-sent events parser and the two stream types over canned bytes.

  defp bytes(text, type \\ "text/event-stream", status \\ 200) do
    ByteStream.new(status, %{"content-type" => type}, [text])
  end

  defp parse(text) do
    {_parser, events} = EventStream.parse(EventStream.new_parser(1024), text)
    events
  end

  defp lines(text) do
    text |> bytes() |> JsonLinesStream.from_source(& &1, 1024) |> Enum.to_list()
  end

  # An opener whose n-th connection (from 1) is the n-th entry of the script: a text, a
  # `ByteStream`, or an exception that the request fails with. It records the last event id of
  # each request in `ids/1`.
  defp script(connections) do
    {:ok, agent} = Agent.start_link(fn -> [] end)

    opener = fn request_opts ->
      headers = Keyword.get(request_opts, :headers, %{})

      number =
        Agent.get_and_update(agent, fn ids ->
          {length(ids) + 1, ids ++ [Map.get(headers, "Last-Event-ID", "")]}
        end)

      connections |> Enum.at(min(number, length(connections)) - 1) |> answer()
    end

    {opener, agent}
  end

  defp ids(agent), do: Agent.get(agent, & &1)

  defp answer(%ByteStream{} = stream), do: {:ok, %{data: stream}}
  defp answer(text) when is_binary(text), do: {:ok, %{data: bytes(text)}}
  defp answer(%{__exception__: true} = exception), do: {:error, exception}

  defp no_content, do: ByteStream.new(204, %{}, [])

  defp sse(connections, opts \\ [reconnect_delay: 1], settings \\ []) do
    {opener, agent} = script(connections)
    {:ok, stream} = SseStream.open(opener, opts, settings)
    {stream, agent}
  end

  defp data_of(stream), do: Enum.map(stream, & &1.data)

  test "bhv streaming 002 a JSON Lines stream yields one decoded item per line and skips blank lines" do
    assert lines("a\n\n  \nb\nc\n") == ["a", "b", "c"]
  end

  test "bhv streaming 003 a JSON Lines stream accepts CRLF line ends and a last line without a newline" do
    assert lines("a\r\nb\r\nc") == ["a", "b", "c"]
  end

  test "bhv streaming 004 a JSON Lines line that does not decode raises a serialization error naming the 1-based line and ends the stream after the items before it" do
    decoder = fn line ->
      case Integer.parse(line) do
        {number, ""} -> number
        _ -> raise SerializationError, message: "not a number"
      end
    end

    stream = "1\n2\nnope\n4\n" |> bytes() |> JsonLinesStream.from_source(decoder, 1024)
    parent = self()

    error =
      assert_raise SerializationError, fn ->
        Enum.each(stream, fn item -> send(parent, {:item, item}) end)
      end

    assert error.message =~ "Line 3"
    assert_received {:item, 1}
    assert_received {:item, 2}
    refute_received {:item, 4}
  end

  test "bhv streaming 005 a line or event longer than the limit raises a serialization error and ends the stream" do
    lines =
      ("ok\n" <> String.duplicate("x", 50) <> "\nlater\n")
      |> bytes()
      |> JsonLinesStream.from_source(& &1, 10)

    parent = self()

    assert_raise SerializationError, fn ->
      Enum.each(lines, fn item -> send(parent, {:line, item}) end)
    end

    assert_received {:line, "ok"}
    refute_received {:line, "later"}

    {events, _agent} =
      sse(["data: " <> String.duplicate("y", 50) <> "\n\n"],
        reconnect_delay: 1,
        max_event_bytes: 10
      )

    assert_raise SerializationError, fn -> Enum.to_list(events) end
  end

  test "bhv sse001 an SSE stream yields the id, event, data and retry fields of each event with message as the default" do
    assert [first, second] = parse("id: 7\nevent: tick\nretry: 250\ndata: one\n\ndata: two\n\n")
    assert first == %{id: "7", event: "tick", data: "one", retry: 250}
    assert second.event == "message"
    assert second.data == "two"
    assert second.id == "7", "the id persists until another arrives"

    {stream, _agent} = sse(["id: 7\nevent: tick\nretry: 250\ndata: one\n\n"])

    assert [%ServerSentEvent{id: "7", event: "tick", data: "one", retry: 250, raw: "one"}] =
             Enum.take(stream, 1)
  end

  test "bhv sse002 an SSE stream joins consecutive data lines with a newline and skips comment lines" do
    assert [%{data: "one\ntwo"}] = parse(": a comment\ndata: one\n: another\ndata: two\n\n")
  end

  test "bhv sse003 an SSE stream drops a leading byte order mark, accepts CR, LF and CRLF line ends and removes one space after the colon" do
    for end_of_line <- ["\n", "\r", "\r\n"] do
      text = "﻿data: a" <> end_of_line <> "data:  b" <> end_of_line <> end_of_line
      assert [%{data: "a\n b"}] = parse(text), inspect(end_of_line)
    end

    assert [%{data: "x"}] = parse("data:x\n\n"), "no space to remove"

    {parser, events} = EventStream.parse(EventStream.new_parser(1024), "data: a\r")
    {_parser, more} = EventStream.parse(parser, "\ndata: b\r\n\r\n")

    assert events ++ more == [%{id: nil, event: "message", data: "a\nb", retry: nil}],
           "a CRLF split across two chunks is one line end"
  end

  test "bhv sse004 an SSE event with an empty data buffer is not delivered but its id is kept" do
    {parser, events} = EventStream.parse(EventStream.new_parser(1024), "id: 9\n\n")

    assert events == []
    assert EventStream.last_event_id(parser) == "9"
  end

  test "bhv sse005 an SSE data payload is decoded as the declared schema and a payload that does not decode raises a serialization error and ends the stream" do
    decoder = fn data ->
      case Integer.parse(data) do
        {number, ""} -> number
        _ -> raise SerializationError, message: "not a number"
      end
    end

    {stream, _agent} =
      sse(["data: 1\n\ndata: oops\n\ndata: 3\n\n"], [reconnect_delay: 1], decoder: decoder)

    parent = self()

    assert_raise SerializationError, fn ->
      Enum.each(stream, fn event -> send(parent, {:data, event.data}) end)
    end

    assert_received {:data, 1}
    refute_received {:data, 3}
  end

  test "bhv sse008 an SSE reconnect waits the server retry value, else the reconnect delay doubling to a cap" do
    assert EventStream.reconnect_wait(3, 1000, 250, nil, 0.0) == 250
    assert EventStream.reconnect_wait(1, 1000, nil, nil, 0.0) == 1000
    assert EventStream.reconnect_wait(2, 1000, nil, nil, 0.0) == 2000
    assert EventStream.reconnect_wait(3, 1000, nil, nil, 0.0) == 4000
    assert EventStream.reconnect_wait(10, 1000, nil, nil, 0.0) == 30_000
    assert EventStream.reconnect_wait(1, 1000, nil, nil, 1.0) == 750, "jitter takes up to 25%"
    assert EventStream.reconnect_wait(1, 1000, nil, 5000, 0.0) == 5000, "Retry-After replaces it"
  end

  test "bhv sse009 an SSE GET stream stops after five consecutive failed reconnects by default and raises the last failure" do
    {stream, agent} = sse([""])

    assert_raise StreamError, fn -> Enum.to_list(stream) end
    assert length(ids(agent)) == 6, "one request and five reconnects"
  end

  test "bhv sse010 an SSE reconnect counter resets after an event is received" do
    {stream, _agent} =
      sse(["data: a\n\n", "data: b\n\n", "data: c\n\n", ""],
        reconnect_delay: 1,
        max_reconnects: 1
      )

    parent = self()

    assert_raise StreamError, fn ->
      Enum.each(stream, fn event -> send(parent, {:data, event.data}) end)
    end

    assert_received {:data, "a"}
    assert_received {:data, "b"}
    assert_received {:data, "c"}
  end

  test "bhv sse011 an SSE stream does not reconnect after a 204, a 4xx status or a response that is not an event stream" do
    {none, agent} = sse([no_content()])
    assert data_of(none) == []
    assert length(ids(agent)) == 1

    missing = ApiError.exception(message: "missing", status_code: 404, response_body: "{}")
    {refused, _agent} = sse(["data: a\n\n", missing])
    assert %{status_code: 404} = assert_raise(ApiError, fn -> Enum.to_list(refused) end)

    html = ByteStream.new(200, %{"content-type" => "text/html"}, [])
    {wrong, agent} = sse(["data: a\n\n", html])
    assert_raise SerializationError, fn -> Enum.to_list(wrong) end
    assert length(ids(agent)) == 2, "asked once more, then refused the answer"
  end

  test "bhv sse012 an SSE stream sends the initial last event id of the call on its first request" do
    {stream, agent} = sse(["data: a\n\n", no_content()], reconnect_delay: 1, last_event_id: "42")

    assert data_of(stream) == ["a"]
    assert hd(ids(agent)) == "42"
  end

  test "bhv sse014 an SSE retry field that is not digits and an id containing NUL are ignored" do
    {parser, [event]} =
      EventStream.parse(EventStream.new_parser(1024), "retry: 12x\nid: a\0b\ndata: x\n\n")

    assert event.data == "x"
    assert event.id == nil
    assert EventStream.retry(parser) == nil
  end

  test "bhv sse016 an SSE request with a body is not reconnected unless the caller turns reconnect on" do
    {off, agent} = sse(["data: a\n\n", "data: b\n\n"], [reconnect_delay: 1], has_body: true)
    assert data_of(off) == ["a"]
    assert length(ids(agent)) == 1

    {on, _agent} =
      sse(["data: a\n\n", "data: b\n\n", no_content()], [reconnect_delay: 1, max_reconnects: 2],
        has_body: true
      )

    assert data_of(on) == ["a", "b"]

    {single_use, once_agent} =
      sse(["data: a\n\n", "data: b\n\n", no_content()], [reconnect_delay: 1, max_reconnects: 5],
        has_body: true,
        single_use_body: true
      )

    assert data_of(single_use) == ["a"]
    assert length(ids(once_agent)) == 1, "a single use body is never sent again"
  end

  test "bhv sse017 an SSE event with an id and no data is not dispatched but updates the last event id and a reconnect resumes from it" do
    {stream, agent} = sse(["data: a\nid: 1\n\nid: 2\n\n", "data: b\nid: 3\n\n", no_content()])

    assert data_of(stream) == ["a", "b"]
    assert Enum.take(ids(agent), 2) == ["", "2"]
  end

  test "bhv sse019 an SSE event cut off by the end of the connection is discarded" do
    assert [%{data: "whole"}] = parse("data: whole\n\ndata: cut off\n")
    assert parse("data: no newline at all") == []
  end

  test "bhv sse020 an SSE reconnect sends Last-Event-ID only when the last event id is not empty" do
    {stream, agent} = sse(["data: a\n\n", "data: b\nid: 5\n\n", no_content()])

    assert data_of(stream) == ["a", "b"]
    assert Enum.take(ids(agent), 2) == ["", ""], "no id yet, so no header on the reconnect"

    {resumed, agent} = sse(["data: a\nid: 5\n\n", no_content()])
    assert data_of(resumed) == ["a"]
    assert ids(agent) == ["", "5"]
  end

  test "bhv sse021 the configured SSE terminator ends the stream and is not delivered" do
    {stream, _agent} =
      sse(["data: a\n\ndata: [DONE]\n\ndata: b\n\n"], [reconnect_delay: 1], terminator: "[DONE]")

    assert data_of(stream) == ["a"]
  end

  test "bhv sse022 an SSE event named error raises the API error with the status and the data and any other name is an item" do
    {stream, agent} =
      sse([
        "event: update\ndata: fine\n\nevent: error\ndata: {\"why\":\"bad\"}\n\n",
        "data: again\n\n"
      ])

    parent = self()

    failure =
      assert_raise ApiError, fn ->
        Enum.each(stream, fn event -> send(parent, {:event, event.event}) end)
      end

    assert failure.status_code == 200
    assert failure.response_body == ~s({"why":"bad"})
    assert_received {:event, "update"}
    assert length(ids(agent)) == 1, "an error event is not reconnected"
  end

  test "bhv sse023 an unknown SSE field is ignored" do
    assert [%{data: "x"}] = parse("mystery: 1\ndata: x\nanother\n\n")
  end
end
