package com.project.Anusha.config;

import com.project.Anusha.model.Bank;
import com.project.Anusha.repository.BankRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds the banks table with Indian banks on first startup.
 * Safe to run multiple times — skips if data already exists.
 */
@Component
public class BankSeeder implements CommandLineRunner {

    @Autowired
    private BankRepository bankRepository;

    @Override
    public void run(String... args) {
        if (bankRepository.count() > 0) return; // Already seeded

        List<Bank> banks = List.of(
            new Bank("State Bank of India", "SBI", "SBIN"),
            new Bank("HDFC Bank", "HDFC", "HDFC"),
            new Bank("ICICI Bank", "ICICI", "ICIC"),
            new Bank("Axis Bank", "AXIS", "UTIB"),
            new Bank("Kotak Mahindra Bank", "KOTAK", "KKBK"),
            new Bank("Punjab National Bank", "PNB", "PUNB"),
            new Bank("Bank of Baroda", "BOB", "BARB"),
            new Bank("Canara Bank", "CANARA", "CNRB"),
            new Bank("Union Bank of India", "UNION", "UBIN"),
            new Bank("Bank of India", "BOI", "BKID"),
            new Bank("Indian Bank", "INDIAN", "IDIB"),
            new Bank("Central Bank of India", "CBI", "CBIN"),
            new Bank("Indian Overseas Bank", "IOB", "IOBA"),
            new Bank("UCO Bank", "UCO", "UCBA"),
            new Bank("Bank of Maharashtra", "BOM", "MAHB"),
            new Bank("Punjab & Sind Bank", "PSB", "PSIB"),
            new Bank("YES Bank", "YES", "YESB"),
            new Bank("IDFC First Bank", "IDFC", "IDFB"),
            new Bank("Federal Bank", "FEDERAL", "FDRL"),
            new Bank("South Indian Bank", "SIB", "SIBL"),
            new Bank("Karnataka Bank", "KBL", "KARB"),
            new Bank("Karur Vysya Bank", "KVB", "KVBL"),
            new Bank("City Union Bank", "CUB", "CIUB"),
            new Bank("Dhanlaxmi Bank", "DLB", "DLXB"),
            new Bank("Jammu & Kashmir Bank", "JKB", "JAKA"),
            new Bank("Lakshmi Vilas Bank", "LVB", "LAVB"),
            new Bank("Nainital Bank", "NTL", "NTBL"),
            new Bank("RBL Bank", "RBL", "RATN"),
            new Bank("IndusInd Bank", "INDUSIND", "INDB"),
            new Bank("Bandhan Bank", "BANDHAN", "BDBL"),
            new Bank("AU Small Finance Bank", "AU", "AUBL"),
            new Bank("Ujjivan Small Finance Bank", "UJJIVAN", "UJVN"),
            new Bank("Equitas Small Finance Bank", "EQUITAS", "ESFB"),
            new Bank("Suryoday Small Finance Bank", "SURYODAY", "SURY"),
            new Bank("Jana Small Finance Bank", "JANA", "JSFB"),
            new Bank("ESAF Small Finance Bank", "ESAF", "ESAF"),
            new Bank("Capital Small Finance Bank", "CAPITAL", "CLBL"),
            new Bank("Paytm Payments Bank", "PAYTM", "PYTM"),
            new Bank("Airtel Payments Bank", "AIRTEL", "AIRP"),
            new Bank("India Post Payments Bank", "IPPB", "IPPB"),
            new Bank("Fino Payments Bank", "FINO", "FINO"),
            new Bank("Andhra Bank", "ANDHRA", "ANDB"),
            new Bank("Corporation Bank", "CORP", "CORP"),
            new Bank("Syndicate Bank", "SYNDICATE", "SYNB"),
            new Bank("Allahabad Bank", "ALLAHABAD", "ALLA"),
            new Bank("Vijaya Bank", "VIJAYA", "VIJB"),
            new Bank("Dena Bank", "DENA", "BKDN"),
            new Bank("Oriental Bank of Commerce", "OBC", "ORBC"),
            new Bank("United Bank of India", "UNITED", "UTBI"),
            new Bank("Andhra Pradesh Grameena Vikas Bank", "APGVB", "APGV"),
            new Bank("Telangana Grameena Bank", "TGB", "TGMB"),
            new Bank("Deccan Grameena Bank", "DGB", "DCGB")
        );

        bankRepository.saveAll(banks);
        System.out.println("[BankSeeder] Seeded " + banks.size() + " banks.");
    }
}
