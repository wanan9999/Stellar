package roro.stellar.manager.carrier;

import android.os.Bundle;

interface ICarrierOverrideService {
    List<Bundle> listSims() = 1;
    Bundle applyOverride(int subId, String countryIso, String carrierName) = 2;
    Bundle resetOverride(int subId) = 3;
    Bundle peek(int subId) = 4;
    Bundle reapplyStored() = 5;
    void setAutoReapply(boolean enabled) = 6;
}
