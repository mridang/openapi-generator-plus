package com.example.petstore.api.options;

import com.example.petstore.models.PetCriteria;
import java.util.List;
import javax.annotation.Nullable;

/** Options for the getPetTag operation. */
public final class GetPetTagOptions {
  @Nullable private List<String> colors;
  @Nullable private List<String> sizes;
  @Nullable private String filter;
  @Nullable private String revision;
  @Nullable private PetCriteria criteria;
  @Nullable private PetCriteria criteriaCsv;
  @Nullable private PetCriteria criteriaDeep;
  @Nullable private byte[] fingerprint;
  @Nullable private PetCriteria xCriteria;
  @Nullable private byte[] xFingerprint;

  /** Creates an options instance with the required parameters. */
  public GetPetTagOptions() {}

  /**
   * Sets the {@code colors} parameter.
   *
   * @param colors the {@code colors} parameter
   * @return this options instance for chaining
   */
  public GetPetTagOptions colors(List<String> colors) {
    this.colors = colors == null ? null : java.util.List.copyOf(colors);
    return this;
  }

  /**
   * Returns the {@code colors} parameter.
   *
   * @return the {@code colors} parameter, or {@code null} if unset
   */
  @Nullable
  public List<String> colors() {
    return colors;
  }

  /**
   * Sets the {@code sizes} parameter.
   *
   * @param sizes the {@code sizes} parameter
   * @return this options instance for chaining
   */
  public GetPetTagOptions sizes(List<String> sizes) {
    this.sizes = sizes == null ? null : java.util.List.copyOf(sizes);
    return this;
  }

  /**
   * Returns the {@code sizes} parameter.
   *
   * @return the {@code sizes} parameter, or {@code null} if unset
   */
  @Nullable
  public List<String> sizes() {
    return sizes;
  }

  /**
   * Sets the {@code filter} parameter.
   *
   * @param filter the {@code filter} parameter
   * @return this options instance for chaining
   */
  public GetPetTagOptions filter(String filter) {
    this.filter = filter;
    return this;
  }

  /**
   * Returns the {@code filter} parameter.
   *
   * @return the {@code filter} parameter, or {@code null} if unset
   */
  @Nullable
  public String filter() {
    return filter;
  }

  /**
   * Sets the {@code revision} parameter.
   *
   * <p>Query value whose RFC 3986 reserved characters must be sent literally (OAS allowReserved),
   * for example a version expression such as v1.0/beta:rc1 keeping the slash and colon.
   *
   * @param revision Query value whose RFC 3986 reserved characters must be sent literally (OAS
   *     allowReserved), for example a version expression such as v1.0/beta:rc1 keeping the slash
   *     and colon.
   * @return this options instance for chaining
   */
  public GetPetTagOptions revision(String revision) {
    this.revision = revision;
    return this;
  }

  /**
   * Returns the {@code revision} parameter.
   *
   * <p>Query value whose RFC 3986 reserved characters must be sent literally (OAS allowReserved),
   * for example a version expression such as v1.0/beta:rc1 keeping the slash and colon.
   *
   * @return the {@code revision} parameter, or {@code null} if unset
   */
  @Nullable
  public String revision() {
    return revision;
  }

  /**
   * Sets the {@code criteria} parameter.
   *
   * <p>Object-valued query parameter, form style exploded (the OAS default for query).
   *
   * @param criteria Object-valued query parameter, form style exploded (the OAS default for query).
   * @return this options instance for chaining
   */
  public GetPetTagOptions criteria(PetCriteria criteria) {
    this.criteria = criteria;
    return this;
  }

  /**
   * Returns the {@code criteria} parameter.
   *
   * <p>Object-valued query parameter, form style exploded (the OAS default for query).
   *
   * @return the {@code criteria} parameter, or {@code null} if unset
   */
  @Nullable
  public PetCriteria criteria() {
    return criteria;
  }

  /**
   * Sets the {@code criteriaCsv} parameter.
   *
   * @param criteriaCsv the {@code criteriaCsv} parameter
   * @return this options instance for chaining
   */
  public GetPetTagOptions criteriaCsv(PetCriteria criteriaCsv) {
    this.criteriaCsv = criteriaCsv;
    return this;
  }

  /**
   * Returns the {@code criteriaCsv} parameter.
   *
   * @return the {@code criteriaCsv} parameter, or {@code null} if unset
   */
  @Nullable
  public PetCriteria criteriaCsv() {
    return criteriaCsv;
  }

  /**
   * Sets the {@code criteriaDeep} parameter.
   *
   * @param criteriaDeep the {@code criteriaDeep} parameter
   * @return this options instance for chaining
   */
  public GetPetTagOptions criteriaDeep(PetCriteria criteriaDeep) {
    this.criteriaDeep = criteriaDeep;
    return this;
  }

  /**
   * Returns the {@code criteriaDeep} parameter.
   *
   * @return the {@code criteriaDeep} parameter, or {@code null} if unset
   */
  @Nullable
  public PetCriteria criteriaDeep() {
    return criteriaDeep;
  }

  /**
   * Sets the {@code fingerprint} parameter.
   *
   * <p>Opaque bytes, base64 on the wire (OAS format byte).
   *
   * @param fingerprint Opaque bytes, base64 on the wire (OAS format byte).
   * @return this options instance for chaining
   */
  public GetPetTagOptions fingerprint(byte[] fingerprint) {
    this.fingerprint = fingerprint == null ? null : fingerprint.clone();
    return this;
  }

  /**
   * Returns the {@code fingerprint} parameter.
   *
   * <p>Opaque bytes, base64 on the wire (OAS format byte).
   *
   * @return the {@code fingerprint} parameter, or {@code null} if unset
   */
  @Nullable
  public byte[] fingerprint() {
    return fingerprint == null ? null : fingerprint.clone();
  }

  /**
   * Sets the {@code X-Criteria} parameter.
   *
   * @param xCriteria the {@code X-Criteria} parameter
   * @return this options instance for chaining
   */
  public GetPetTagOptions xCriteria(PetCriteria xCriteria) {
    this.xCriteria = xCriteria;
    return this;
  }

  /**
   * Returns the {@code X-Criteria} parameter.
   *
   * @return the {@code X-Criteria} parameter, or {@code null} if unset
   */
  @Nullable
  public PetCriteria xCriteria() {
    return xCriteria;
  }

  /**
   * Sets the {@code X-Fingerprint} parameter.
   *
   * @param xFingerprint the {@code X-Fingerprint} parameter
   * @return this options instance for chaining
   */
  public GetPetTagOptions xFingerprint(byte[] xFingerprint) {
    this.xFingerprint = xFingerprint == null ? null : xFingerprint.clone();
    return this;
  }

  /**
   * Returns the {@code X-Fingerprint} parameter.
   *
   * @return the {@code X-Fingerprint} parameter, or {@code null} if unset
   */
  @Nullable
  public byte[] xFingerprint() {
    return xFingerprint == null ? null : xFingerprint.clone();
  }
}
