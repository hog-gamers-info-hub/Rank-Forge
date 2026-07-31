package com.hoggamers.rankforge.domain.export

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class CsvUtf8FilePayload internal constructor(
    bytes: ByteArray,
    val byteCount: Int,
    val sha256: String,
) {
    private val storedBytes: ByteArray = bytes.copyOf()

    fun bytes(): ByteArray = storedBytes.copyOf()
}

enum class CsvUtf8FileFailure {
    EMPTY_CSV_CONTENT,
    EMPTY_FILE_BYTES,
    UTF8_BOM_PRESENT,
    MALFORMED_UTF8_BYTES,
    DECODED_CONTENT_MISMATCH,
    BYTE_CONTENT_MISMATCH,
    CHECKSUM_MISMATCH,
    CHECKSUM_GENERATION_FAILURE,
}

sealed interface CsvUtf8PayloadResult {
    data class Success(
        val payload: CsvUtf8FilePayload,
    ) : CsvUtf8PayloadResult

    data class Failure(
        val failures: Set<CsvUtf8FileFailure>,
    ) : CsvUtf8PayloadResult
}

sealed interface CsvUtf8ValidationResult {
    data class Success(
        val byteCount: Int,
        val sha256: String,
    ) : CsvUtf8ValidationResult

    data class Failure(
        val failures: Set<CsvUtf8FileFailure>,
    ) : CsvUtf8ValidationResult
}

class CsvUtf8FileValidator {
    fun createPayload(csv: String): CsvUtf8PayloadResult {
        if (csv.isEmpty()) {
            return CsvUtf8PayloadResult.Failure(
                setOf(CsvUtf8FileFailure.EMPTY_CSV_CONTENT),
            )
        }

        val bytes = csv.toByteArray(StandardCharsets.UTF_8)
        val failures = linkedSetOf<CsvUtf8FileFailure>()

        if (bytes.hasUtf8Bom()) {
            failures += CsvUtf8FileFailure.UTF8_BOM_PRESENT
        }

        val checksum = calculateSha256(bytes)
        if (checksum == null) {
            failures += CsvUtf8FileFailure.CHECKSUM_GENERATION_FAILURE
        }

        if (failures.isNotEmpty()) {
            return CsvUtf8PayloadResult.Failure(failures)
        }

        return CsvUtf8PayloadResult.Success(
            payload = CsvUtf8FilePayload(
                bytes = bytes,
                byteCount = bytes.size,
                sha256 = checkNotNull(checksum),
            ),
        )
    }

    fun validate(
        expectedCsv: String,
        actualBytes: ByteArray,
        expectedSha256: String? = null,
    ): CsvUtf8ValidationResult {
        val failures = linkedSetOf<CsvUtf8FileFailure>()

        if (expectedCsv.isEmpty()) {
            failures += CsvUtf8FileFailure.EMPTY_CSV_CONTENT
        }

        if (actualBytes.isEmpty()) {
            failures += CsvUtf8FileFailure.EMPTY_FILE_BYTES
        }

        if (actualBytes.hasUtf8Bom()) {
            failures += CsvUtf8FileFailure.UTF8_BOM_PRESENT
        }

        val decodedContent = strictlyDecodeUtf8(actualBytes)
        if (decodedContent == null) {
            failures += CsvUtf8FileFailure.MALFORMED_UTF8_BYTES
        } else if (decodedContent != expectedCsv) {
            failures += CsvUtf8FileFailure.DECODED_CONTENT_MISMATCH
        }

        val expectedBytes = expectedCsv.toByteArray(StandardCharsets.UTF_8)
        if (!expectedBytes.contentEquals(actualBytes)) {
            failures += CsvUtf8FileFailure.BYTE_CONTENT_MISMATCH
        }

        val calculatedChecksum = calculateSha256(actualBytes)
        if (calculatedChecksum == null) {
            failures += CsvUtf8FileFailure.CHECKSUM_GENERATION_FAILURE
        } else if (
            expectedSha256 != null &&
            !calculatedChecksum.equals(
                other = expectedSha256,
                ignoreCase = true,
            )
        ) {
            failures += CsvUtf8FileFailure.CHECKSUM_MISMATCH
        }

        if (failures.isNotEmpty()) {
            return CsvUtf8ValidationResult.Failure(failures)
        }

        return CsvUtf8ValidationResult.Success(
            byteCount = actualBytes.size,
            sha256 = checkNotNull(calculatedChecksum),
        )
    }

    private fun strictlyDecodeUtf8(
        bytes: ByteArray,
    ): String? =
        runCatching {
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull()

    private fun calculateSha256(
        bytes: ByteArray,
    ): String? =
        runCatching {
            MessageDigest
                .getInstance(SHA_256_ALGORITHM)
                .digest(bytes)
                .toLowercaseHex()
        }.getOrNull()

    private fun ByteArray.hasUtf8Bom(): Boolean =
        size >= UTF8_BOM.size &&
            indices.take(UTF8_BOM.size).all { index ->
                this[index] == UTF8_BOM[index]
            }

    private fun ByteArray.toLowercaseHex(): String =
        buildString(size * 2) {
            this@toLowercaseHex.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(HEX_CHARACTERS[value ushr 4])
                append(HEX_CHARACTERS[value and 0x0f])
            }
        }

    private companion object {
        const val SHA_256_ALGORITHM = "SHA-256"
        const val HEX_CHARACTERS = "0123456789abcdef"

        val UTF8_BOM = byteArrayOf(
            0xef.toByte(),
            0xbb.toByte(),
            0xbf.toByte(),
        )
    }
}
