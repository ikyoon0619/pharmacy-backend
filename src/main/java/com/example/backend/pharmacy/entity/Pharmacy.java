package com.example.backend.pharmacy.entity;


//@Entity(name="pharmacy")
//@Getter
//@Builder
//@NoArgsConstructor
//@AllArgsConstructor
public class Pharmacy {
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String pharmacyName;
    private String pharmacyAddress;
    private double latitude;
    private double longitude;

    public void changePharmacyAddress(String address){
        this.pharmacyAddress = address;
    }

}
