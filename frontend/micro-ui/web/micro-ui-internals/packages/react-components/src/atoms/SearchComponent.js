import React, { useContext, useEffect, useState } from "react";
import { useForm, FormProvider, useFormContext } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { InboxContext } from "../hoc/InboxSearchComposerContext";
import RenderFormFields from "../molecules/RenderFormFields";
import Header from "../atoms/Header";
import LinkLabel from '../atoms/LinkLabel';
import SubmitBar from "../atoms/SubmitBar";
import Toast from "../atoms/Toast";

const SearchComponent = ({ uiConfig, header = "", screenType = "search"}) => {
  const { t } = useTranslation();
  const { state, dispatch } = useContext(InboxContext)
  const [showToast,setShowToast] = useState(null)
  let updatedFields = [];
  const [componentType, setComponentType] = useState(uiConfig?.type);

  const formMethods = useForm({defaultValues: uiConfig?.defaultValues});
  const formState = formMethods?.formState;
  const handleSubmit = formMethods?.handleSubmit;

  const checkKeyDown = (e) => {
    const keyCode = e.keyCode ? e.keyCode : e.key ? e.key : e.which;
    if (keyCode === 13) {
      e.preventDefault();
    }
  };

  useEffect(() => {
    updatedFields = Object.values(formState?.dirtyFields)
  }, [formState])

  const onSubmit = (data) => {
    if(updatedFields.length >= uiConfig?.minReqFields) {
      //run preprocessing functions(use case -> changing date inputs to epoch)
      uiConfig.fields.forEach(field=> {
        if (field.preProcessfn) {
          data[field.populators.name] = field.preProcessfn(data?.[field.populators.name])
          // data[field.populators.name] = new Date(data[field.populators.name]).getTime() / 1000
        }
      })
      dispatch({
        type: "searchForm",
        state: {
          ...data
        }
      })
    } else {
      setShowToast({ warning: true, label: "Please enter minimum 1 search criteria" })
      setTimeout(closeToast, 3000);
    }
  }

  const clearSearch = () => {
    reset(uiConfig?.defaultValues)
    dispatch({
      type: "clearSearchForm",
      state:{}
    })
  }
 
  const closeToast = () => {
    setShowToast(null);
  }

  return (
    <React.Fragment>
      <div className={'search-wrapper'}>
        {header && <Header styles={uiConfig?.headerStyle}>{header}</Header>}
        <FormProvider {...formMethods}>
          <form onSubmit={handleSubmit(onSubmit)} onKeyDown={(e) => checkKeyDown(e)}>
            <div className={`search-field-wrapper ${screenType} ${uiConfig?.type}`}>
              <RenderFormFields 
                fields={uiConfig?.fields} 
                labelStyle={{fontSize: "16px"}}
              />  
              <div className={`search-button-wrapper ${screenType} ${componentType}`}>
                <LinkLabel style={{marginBottom: 0, whiteSpace: 'nowrap'}} onClick={clearSearch}>{uiConfig?.secondaryLabel}</LinkLabel>
                <SubmitBar label={uiConfig?.primaryLabel} submit="submit" disabled={false}/>
              </div>
            </div> 
          </form> 
        </FormProvider>
        { showToast && <Toast 
          error={showToast.error}
          warning={showToast.warning}
          label={t(showToast.label)}
          isDleteBtn={true}
          onClose={closeToast} />
        }
      </div>
    </React.Fragment>
  )
}

export default SearchComponent