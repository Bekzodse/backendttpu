package uz.ttpu.inventory.asset.service;

import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.ttpu.inventory.asset.repository.ItemRepository;

@Service
@RequiredArgsConstructor
public class QrIdService {

    public static final int PUBLIC_ID_LENGTH = 10;
    private static final char[] BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final int MAX_GENERATION_ATTEMPTS = 20;

    private final SecureRandom secureRandom = new SecureRandom();
    private final ItemRepository itemRepository;

    /**
     * Generates a cryptographically random, 10-character Base32 (RFC 4648) non-guessable public ID
     * guaranteed not to collide with existing items.
     */
    public String generateUniquePublicId() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidate = generateRandomBase32(PUBLIC_ID_LENGTH);
            if (!itemRepository.existsByQrCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate a unique 10-character Base32 QR ID after "
                + MAX_GENERATION_ATTEMPTS + " attempts");
    }

    String generateRandomBase32(int length) {
        char[] buffer = new char[length];
        for (int i = 0; i < length; i++) {
            buffer[i] = BASE32_ALPHABET[secureRandom.nextInt(BASE32_ALPHABET.length)];
        }
        return new String(buffer);
    }
}
