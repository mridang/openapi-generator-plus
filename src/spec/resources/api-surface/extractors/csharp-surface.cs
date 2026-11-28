#:property PublishAot=false
#:property TargetFramework=net10.0
#:property Nullable=enable

// Prints the public API records (D180) of a built C# SDK assembly, read with reflection: every
// exported type and its public and protected members, one signature per line with the owning type
// fully qualified, nullable reference annotations and parameter names included. Compiler-generated
// members (`<Clone>$`, accessors, backing fields) are left out; operators are kept.
// Usage: dotnet run csharp-surface.cs -- <path to the SDK .dll>

using System.ComponentModel;
using System.Reflection;
using System.Runtime.CompilerServices;
using System.Text;

var assembly = Assembly.LoadFrom(Path.GetFullPath(args[0]));
var nullability = new NullabilityInfoContext();
var lines = new SortedSet<string>(StringComparer.Ordinal);
const BindingFlags Declared =
    BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.Instance | BindingFlags.Static
    | BindingFlags.DeclaredOnly;

foreach (var type in assembly.GetExportedTypes())
{
    var owner = TypeName(type, null);
    lines.Add("@@T " + owner);
    lines.Add("@@E " + owner);
    var browsable = type.GetCustomAttribute<EditorBrowsableAttribute>();
    if ((type.Namespace ?? "").Split('.').Contains("Internal")
        || browsable?.State == EditorBrowsableState.Never)
    {
        lines.Add("@@I " + owner);
    }
    lines.Add("@@S " + TypeHeader(type));

    if (type.IsEnum)
    {
        foreach (var field in type.GetFields(BindingFlags.Public | BindingFlags.Static))
        {
            lines.Add($"@@S {owner}: {field.Name} = {Convert.ToInt64(field.GetRawConstantValue())}");
        }
        continue;
    }
    if (typeof(Delegate).IsAssignableFrom(type))
    {
        continue;
    }

    foreach (var ctor in type.GetConstructors(Declared).Where(Visible))
    {
        lines.Add($"@@S {owner}: {Access(ctor)}{(ctor.IsStatic ? "static " : "")}.ctor({Parameters(ctor)})");
    }
    foreach (var method in type.GetMethods(Declared).Where(Visible))
    {
        if (method.Name.Contains('<') || (method.IsSpecialName && !method.Name.StartsWith("op_")))
        {
            continue;
        }
        var generic = method.IsGenericMethodDefinition
            ? "<" + string.Join(", ", method.GetGenericArguments().Select(a => a.Name)) + ">"
            : "";
        lines.Add(
            $"@@S {owner}: {Access(method)}{Modifiers(method, type)}"
                + $"{TypeName(method.ReturnType, nullability.Create(method.ReturnParameter))} "
                + $"{method.Name}{generic}({Parameters(method)})");
    }
    foreach (var property in type.GetProperties(Declared))
    {
        var getter = property.GetMethod is { } g && Visible(g) ? g : null;
        var setter = property.SetMethod is { } s && Visible(s) ? s : null;
        var accessor = getter ?? setter;
        if (accessor is null || property.Name.Contains('<'))
        {
            continue;
        }
        var index = property.GetIndexParameters();
        var name = index.Length == 0
            ? property.Name
            : "this[" + string.Join(", ", index.Select(p => Parameter(p))) + "]";
        var accessors = new StringBuilder();
        if (getter is not null)
        {
            accessors.Append(Access(getter) == Access(accessor) ? "get; " : Access(getter) + "get; ");
        }
        if (setter is not null)
        {
            var init = setter.ReturnParameter.GetRequiredCustomModifiers().Contains(typeof(IsExternalInit));
            var prefix = Access(setter) == Access(accessor) ? "" : Access(setter);
            accessors.Append(prefix).Append(init ? "init; " : "set; ");
        }
        var required = property.GetCustomAttribute<RequiredMemberAttribute>() is not null ? "required " : "";
        lines.Add(
            $"@@S {owner}: {Access(accessor)}{required}{Modifiers(accessor, type)}"
                + $"{TypeName(property.PropertyType, nullability.Create(property))} {name} {{ {accessors}}}");
    }
    foreach (var field in type.GetFields(Declared).Where(f => f.IsPublic || f.IsFamily || f.IsFamilyOrAssembly))
    {
        if (field.Name.Contains('<'))
        {
            continue;
        }
        var kind = field.IsLiteral ? "const " : (field.IsStatic ? "static " : "") + (field.IsInitOnly ? "readonly " : "");
        var value = field.IsLiteral ? " = " + Literal(field.GetRawConstantValue()) : "";
        lines.Add(
            $"@@S {owner}: {FieldAccess(field)}{kind}"
                + $"{TypeName(field.FieldType, nullability.Create(field))} {field.Name}{value}");
    }
    foreach (var ev in type.GetEvents(Declared))
    {
        if (ev.AddMethod is { } add && Visible(add))
        {
            lines.Add(
                $"@@S {owner}: {Access(add)}{Modifiers(add, type)}event "
                    + $"{TypeName(ev.EventHandlerType!, nullability.Create(ev))} {ev.Name}");
        }
    }
}

foreach (var line in lines)
{
    Console.WriteLine(line);
}

static bool Visible(MethodBase m) => m.IsPublic || m.IsFamily || m.IsFamilyOrAssembly;

static string Access(MethodBase m) => m.IsPublic ? "public " : "protected ";

static string FieldAccess(FieldInfo f) => f.IsPublic ? "public " : "protected ";

static string Modifiers(MethodInfo m, Type owner)
{
    if (m.IsStatic)
    {
        return m.IsAbstract ? "static abstract " : m.IsVirtual ? "static virtual " : "static ";
    }
    if (owner.IsInterface)
    {
        return m.IsAbstract ? "" : "default ";
    }
    if (m.IsAbstract)
    {
        return "abstract ";
    }
    if (!m.IsVirtual || m.IsFinal && m.GetBaseDefinition() == m)
    {
        return "";
    }
    var sealedness = m.IsFinal ? "sealed " : "";
    return m.GetBaseDefinition() != m ? sealedness + "override " : "virtual ";
}

string Parameters(MethodBase m) => string.Join(", ", m.GetParameters().Select(p => Parameter(p)));

string Parameter(ParameterInfo p)
{
    var type = p.ParameterType;
    var prefix = "";
    if (type.IsByRef)
    {
        prefix = p.IsOut ? "out " : p.IsIn ? "in " : "ref ";
        type = type.GetElementType()!;
    }
    if (p.GetCustomAttribute<ParamArrayAttribute>() is not null)
    {
        prefix += "params ";
    }
    var text = prefix + TypeName(type, type == p.ParameterType ? nullability.Create(p) : null) + " " + p.Name;
    if (p.HasDefaultValue)
    {
        var raw = p.RawDefaultValue;
        text += " = " + (raw is null && type.IsValueType && Nullable.GetUnderlyingType(type) is null
            ? "default"
            : Literal(raw));
    }
    return text;
}

static string Literal(object? value) => value switch
{
    null => "null",
    string s => "\"" + s + "\"",
    bool b => b ? "true" : "false",
    char c => "'" + c + "'",
    IFormattable f => f.ToString(null, System.Globalization.CultureInfo.InvariantCulture),
    _ => value.ToString() ?? "",
};

static string TypeHeader(Type type)
{
    var name = TypeName(type, null);
    string kind;
    if (type.IsEnum)
    {
        kind = "enum";
    }
    else if (typeof(Delegate).IsAssignableFrom(type) && type != typeof(Delegate))
    {
        var invoke = type.GetMethod("Invoke")!;
        return $"public delegate {TypeName(invoke.ReturnType, null)} {name}("
            + string.Join(", ", invoke.GetParameters().Select(p => TypeName(p.ParameterType, null) + " " + p.Name)) + ")";
    }
    else if (type.IsInterface)
    {
        kind = "interface";
    }
    else if (type.IsValueType)
    {
        kind = (type.GetMethod("<Clone>$") is not null || type.GetProperty("EqualityContract", BindingFlags.NonPublic | BindingFlags.Instance) is not null ? "record " : "") + "struct";
    }
    else
    {
        var record = type.GetMethod("<Clone>$") is not null ? "record" : "class";
        kind = type.IsAbstract && type.IsSealed ? "static " + record
            : type.IsAbstract ? "abstract " + record
            : type.IsSealed ? "sealed " + record
            : record;
    }
    var bases = new List<string>();
    if (type.BaseType is { } b && b != typeof(object) && b != typeof(ValueType) && b != typeof(Enum))
    {
        bases.Add(TypeName(b, null));
    }
    if (type.IsEnum)
    {
        bases.Add(TypeName(Enum.GetUnderlyingType(type), null));
    }
    else
    {
        var inherited = type.BaseType?.GetInterfaces() ?? Type.EmptyTypes;
        bases.AddRange(
            type.GetInterfaces()
                .Where(i => !inherited.Contains(i))
                .Select(i => TypeName(i, null))
                .OrderBy(n => n, StringComparer.Ordinal));
    }
    return $"public {kind} {name}" + (bases.Count > 0 ? " : " + string.Join(", ", bases) : "");
}

static string TypeName(Type type, NullabilityInfo? info)
{
    var nullable = info?.ReadState == NullabilityState.Nullable && !type.IsValueType ? "?" : "";
    if (type.IsGenericParameter)
    {
        return type.Name + nullable;
    }
    if (type.IsArray)
    {
        return TypeName(type.GetElementType()!, info?.ElementType) + "[" + new string(',', type.GetArrayRank() - 1) + "]" + nullable;
    }
    if (type.IsByRef || type.IsPointer)
    {
        return TypeName(type.GetElementType()!, null) + (type.IsByRef ? "&" : "*");
    }
    if (Nullable.GetUnderlyingType(type) is { } underlying)
    {
        return TypeName(underlying, info?.GenericTypeArguments.FirstOrDefault()) + "?";
    }
    var keyword = Keyword(type);
    if (keyword is not null)
    {
        return keyword + nullable;
    }
    var name = type.IsNested ? TypeName(type.DeclaringType!, null) + "." + type.Name : (type.Namespace is null ? "" : type.Namespace + ".") + type.Name;
    var tick = name.IndexOf('`');
    if (tick >= 0)
    {
        name = name[..tick];
    }
    if (type.IsGenericType)
    {
        var args = type.GetGenericArguments();
        var own = type.IsNested && type.DeclaringType!.IsGenericType
            ? args.Skip(type.DeclaringType.GetGenericArguments().Length).ToArray()
            : args;
        var offset = args.Length - own.Length;
        if (own.Length > 0)
        {
            name += "<" + string.Join(", ", own.Select((a, i) =>
                TypeName(a, info is not null && info.GenericTypeArguments.Length == args.Length ? info.GenericTypeArguments[offset + i] : null))) + ">";
        }
    }
    return name + nullable;
}

static string? Keyword(Type type) => type.IsEnum ? null : Type.GetTypeCode(type) switch
{
    TypeCode.Boolean => "bool",
    TypeCode.Byte => "byte",
    TypeCode.SByte => "sbyte",
    TypeCode.Char => "char",
    TypeCode.Int16 => "short",
    TypeCode.UInt16 => "ushort",
    TypeCode.Int32 => "int",
    TypeCode.UInt32 => "uint",
    TypeCode.Int64 => "long",
    TypeCode.UInt64 => "ulong",
    TypeCode.Single => "float",
    TypeCode.Double => "double",
    TypeCode.Decimal => "decimal",
    TypeCode.String => "string",
    _ when type == typeof(object) => "object",
    _ when type == typeof(void) => "void",
    _ => null,
};
