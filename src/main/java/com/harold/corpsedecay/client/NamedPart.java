package com.harold.corpsedecay.client;

/** Mixed into ModelPart: the name a model looked the part up by ("head", "left_hind_leg"...). */
public interface NamedPart {
    String corpsedecay$getName();

    void corpsedecay$setName(String name);
}
