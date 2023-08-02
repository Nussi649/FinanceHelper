package Logic;

import java.util.ArrayList;

public class ProjectBudgetBE extends BudgetAccountBE {

    // indivYearlyBudget now equals the total budget for the project
    public ProjectBudgetBE(String name, float totalBudget) {
        super(name);
        indivYearlyBudget = totalBudget;
        indivAvailableBudget = totalBudget;
    }

    public ProjectBudgetBE(AccountBE source) {
        super(source);
    }

    @Override
    public void reset() {
        txList = new ArrayList<>();
    }

    // region there is no renewal
    @Override
    public void tryRenew() { }

    @Override
    public void setAutoRenew(boolean autoRenew) {
        this.autoRenew = false;
    }

    @Override
    public void setRenewalPeriod(int duration) { }

    @Override
    public void setNextRenewal(String renewalPeriod) throws IllegalArgumentException { }

    @Override
    public void incrementRenewalPeriod() { }
    // endregion

    @Override
    public void adjustIndivYearlyBudget(float delta) {
        super.adjustIndivYearlyBudget(delta);
        indivAvailableBudget = indivYearlyBudget;
    }

    @Override
    public float getMeanAllottedIndivBudget() {
        return indivYearlyBudget;
    }
}
