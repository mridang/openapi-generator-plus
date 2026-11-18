from types import MappingProxyType
from typing import Optional

from petstore_client.call_hook import (
    CallHook,
    CallInfo,
    CallObserver,
    CallOutcome,
    chain,
)


class _Log:
    def __init__(self) -> None:
        self.events: list[str] = []


class _Hook:
    def __init__(self, name: str, log: _Log, observe: bool = True) -> None:
        self._name = name
        self._log = log
        self._observe = observe

    def start(self, info: CallInfo, headers: dict[str, str]) -> Optional[CallObserver]:
        self._log.events.append(f"start {self._name}")
        return _Observer(self._name, self._log) if self._observe else None


class _Observer:
    def __init__(self, name: str, log: _Log) -> None:
        self._name = name
        self._log = log

    def finish(self, outcome: CallOutcome) -> None:
        self._log.events.append(f"finish {self._name}")


_INFO = CallInfo(
    operation_id="getItem",
    method="GET",
    url_template="/items/{id}",
    url="https://example.test/items/7",
    server_address="example.test",
)


class TestChain:
    def test_bhv_hooks_020_chain_starts_in_order_and_finishes_in_reverse_order(
        self,
    ) -> None:
        log = _Log()
        hook = chain(_Hook("a", log), _Hook("b", log), _Hook("c", log))

        observer = hook.start(_INFO, {})
        assert observer is not None
        observer.finish(CallOutcome(status_code=200, headers={}, error=None))

        assert log.events == [
            "start a",
            "start b",
            "start c",
            "finish c",
            "finish b",
            "finish a",
        ]

    def test_bhv_hooks_020_a_hook_that_returns_nothing_is_skipped_when_finishing(
        self,
    ) -> None:
        log = _Log()
        hook = chain(
            _Hook("a", log), _Hook("quiet", log, observe=False), _Hook("c", log)
        )

        observer = hook.start(_INFO, {})
        assert observer is not None
        observer.finish(CallOutcome(status_code=200, headers={}, error=None))

        assert log.events == [
            "start a",
            "start quiet",
            "start c",
            "finish c",
            "finish a",
        ]

    def test_a_chain_of_no_hooks_does_nothing(self) -> None:
        observer = chain().start(_INFO, {})

        assert observer is not None
        observer.finish(CallOutcome(status_code=0, headers=None, error=None))

    def test_a_chain_is_a_call_hook(self) -> None:
        assert isinstance(chain(), CallHook)

    def test_one_failing_observer_does_not_stop_the_others(self) -> None:
        log = _Log()

        class _Broken:
            def start(
                self, info: CallInfo, headers: dict[str, str]
            ) -> Optional[CallObserver]:
                return self

            def finish(self, outcome: CallOutcome) -> None:
                raise RuntimeError("observer broke")

        observer = chain(_Hook("a", log), _Broken()).start(_INFO, {})
        assert observer is not None
        observer.finish(CallOutcome(status_code=200, headers={}, error=None))

        assert log.events == ["start a", "finish a"]


class TestCallOutcome:
    def test_the_response_headers_are_lower_cased_and_read_only(self) -> None:
        outcome = CallOutcome(status_code=200, headers={"X-Mixed": "v"}, error=None)

        assert outcome.headers is not None
        assert outcome.headers["x-mixed"] == "v"
        assert isinstance(outcome.headers, MappingProxyType)

    def test_no_response_means_no_headers_rather_than_an_empty_map(self) -> None:
        assert CallOutcome(status_code=0, headers=None, error=None).headers is None
