using System.Diagnostics;
using System.Globalization;

namespace PetstoreClient;

/// <summary>
/// Utility for injecting W3C Trace Context headers (<c>traceparent</c>, <c>tracestate</c>)
/// into outgoing API requests when an active <see cref="Activity"/> exists.
///
/// If no <see cref="Activity.Current"/> is present, this class silently no-ops.
/// </summary>
public static class TraceContextUtil
{
    /// <summary>
    /// Return a dictionary containing <c>traceparent</c> and optionally <c>tracestate</c>
    /// headers derived from <see cref="Activity.Current"/>.
    ///
    /// If there is no current activity, an empty dictionary is returned.
    /// </summary>
    /// <returns>Trace context headers, or an empty dictionary.</returns>
    public static Dictionary<string, string> GetTraceHeaders()
    {
        Dictionary<string, string> headers = [];

        Activity? activity = Activity.Current;
        if (activity is null)
        {
            return headers;
        }

        string traceId = activity.TraceId.ToString();
        string spanId = activity.SpanId.ToString();
        string traceFlags = ((int)activity.ActivityTraceFlags).ToString(
            "x2",
            CultureInfo.InvariantCulture
        );
        headers["traceparent"] = $"00-{traceId}-{spanId}-{traceFlags}";

        if (!string.IsNullOrEmpty(activity.TraceStateString))
        {
            headers["tracestate"] = activity.TraceStateString;
        }

        return headers;
    }
}
