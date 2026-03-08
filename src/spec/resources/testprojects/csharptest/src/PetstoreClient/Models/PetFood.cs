using System.Text.Json.Serialization;

namespace PetstoreClient.Models;

/// <summary>
/// Food for pets, discriminated by foodType
/// </summary>
[JsonPolymorphic(TypeDiscriminatorPropertyName = "foodType")]
[JsonDerivedType(typeof(DryFood), "dry")]
[JsonDerivedType(typeof(WetFood), "wet")]
[JsonDerivedType(typeof(DryFood), "DryFood")]
[JsonDerivedType(typeof(WetFood), "WetFood")]
public abstract class PetFood { }
