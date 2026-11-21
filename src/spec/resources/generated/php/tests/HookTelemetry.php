<?php

declare(strict_types=1);

namespace PetstoreClient\Test;

use OpenTelemetry\API\Metrics\MeterProviderInterface;
use OpenTelemetry\SDK\Metrics\Data\Histogram;
use OpenTelemetry\SDK\Metrics\Data\HistogramDataPoint;
use OpenTelemetry\SDK\Metrics\Data\Metric;
use OpenTelemetry\SDK\Metrics\Data\NumberDataPoint;
use OpenTelemetry\SDK\Metrics\Data\Sum;
use OpenTelemetry\SDK\Metrics\MeterProvider;
use OpenTelemetry\SDK\Metrics\MetricExporter\InMemoryExporter as MetricMemoryExporter;
use OpenTelemetry\SDK\Metrics\MetricReader\ExportingReader;
use OpenTelemetry\SDK\Trace\SpanExporter\InMemoryExporter as SpanMemoryExporter;
use OpenTelemetry\SDK\Trace\SpanProcessor\SimpleSpanProcessor;
use OpenTelemetry\SDK\Trace\TracerProvider;

/**
 * OpenTelemetry providers that record into memory, and readers for what they recorded.
 */
final class HookTelemetry
{
    /**
     * A tracer provider and a meter provider that record into memory.
     *
     * @return array{TracerProvider, SpanMemoryExporter, MeterProviderInterface, ExportingReader, MetricMemoryExporter}
     */
    public static function providers(): array
    {
        $spans = new SpanMemoryExporter();
        $metrics = new MetricMemoryExporter();
        $reader = new ExportingReader($metrics);

        return [
            new TracerProvider(new SimpleSpanProcessor($spans)),
            $spans,
            MeterProvider::builder()->addReader($reader)->build(),
            $reader,
            $metrics,
        ];
    }

    /**
     * The metrics recorded so far, by name.
     *
     * @return array<string, Metric>
     */
    public static function metrics(ExportingReader $reader, MetricMemoryExporter $exporter): array
    {
        $reader->collect();
        $byName = [];
        foreach ($exporter->collect(true) as $metric) {
            $byName[$metric->name] = $metric;
        }

        return $byName;
    }

    /**
     * The single data point of a metric.
     */
    public static function point(Metric $metric): HistogramDataPoint|NumberDataPoint
    {
        $data = $metric->data;
        if (!$data instanceof Sum && !$data instanceof Histogram) {
            throw new \LogicException('Expected a sum or a histogram');
        }
        /** @var list<HistogramDataPoint|NumberDataPoint> $points */
        $points = [...$data->dataPoints];
        if (count($points) !== 1) {
            throw new \LogicException('Expected one data point, got ' . count($points));
        }

        return $points[0];
    }

    public static function number(Metric $metric): NumberDataPoint
    {
        $point = self::point($metric);
        if (!$point instanceof NumberDataPoint) {
            throw new \LogicException('Expected a number data point');
        }

        return $point;
    }

    public static function histogram(Metric $metric): HistogramDataPoint
    {
        $point = self::point($metric);
        if (!$point instanceof HistogramDataPoint) {
            throw new \LogicException('Expected a histogram data point');
        }

        return $point;
    }
}
